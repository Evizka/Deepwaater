#!/usr/bin/env python3
"""Smoke test for the Deepwaater mod on a running NeoForge dedicated server.

Used by CI (.github/workflows/build.yml). The server must have been started with
`./gradlew runServer` and must have RCON enabled on port 25575 with the password below.

It waits for the server to finish starting, then runs the world generation features
through the console (RCON) and checks that they really place blocks.
Results are printed as GitHub annotations so they can be read from the Actions UI.
"""
import socket
import struct
import sys
import time

LOG_PATH = sys.argv[1] if len(sys.argv) > 1 else "server-run.log"
HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "ci-smoke-test"

failures = []


def notice(msg):
    print(f"::notice::{msg}", flush=True)


def fail(msg):
    failures.append(msg)
    print(f"::error::{msg}", flush=True)


def read_log():
    with open(LOG_PATH, encoding="utf-8", errors="replace") as f:
        return f.read()


def wait_for_server(timeout=420):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if "Done (" in read_log():
            return True
        time.sleep(3)
    return False


class Rcon:
    """Minimal Source RCON client (the protocol Minecraft uses)."""

    def __init__(self, host, port, password, attempts=30):
        last_error = None
        for _ in range(attempts):
            try:
                self.sock = socket.create_connection((host, port), timeout=60)
                break
            except OSError as e:
                last_error = e
                time.sleep(2)
        else:
            raise last_error
        self.next_id = 1
        self._send(1, 3, password)
        while True:
            rid, typ, _ = self._recv()
            if typ == 2:
                if rid == -1:
                    raise RuntimeError("RCON password rejected")
                break

    def _recv_exact(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            data += chunk
        return data

    def _send(self, rid, typ, payload):
        body = struct.pack("<ii", rid, typ) + payload.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(body)) + body)

    def _recv(self):
        size = struct.unpack("<i", self._recv_exact(4))[0]
        data = self._recv_exact(size)
        rid, typ = struct.unpack("<ii", data[:8])
        return rid, typ, data[8:-2].decode("utf-8", "replace")

    def command(self, cmd):
        rid = self.next_id
        self.next_id += 1
        self._send(rid, 2, cmd)
        while True:
            got_id, typ, payload = self._recv()
            if got_id == rid and typ == 0:
                return payload


def run_checks(rcon):
    log_before = len(read_log())

    # 1. The biome can be found in this world.
    out = rcon.command("locate biome deepwaater:deep_bog")
    notice(f"locate biome: {out.strip()[:200]}")
    if "could not find" in out.lower():
        fail("Deep Bog biome was not found near spawn (may just be rare, check the seed)")

    # 2. Swamp tree: place it and check that its trunk and deep roots exist.
    out = rcon.command("place feature deepwaater:swamp_tree 0 80 0")
    notice(f"place swamp_tree: {out.strip()[:200]}")
    if any(word in out.lower() for word in ("failed", "unknown", "error")):
        fail(f"swamp_tree placement failed: {out.strip()[:200]}")
    rcon.command("execute if block 0 81 0 deepwaater:deep_bog_log run say SMOKE_TRUNK_OK")
    rcon.command("execute if block 0 75 0 deepwaater:deep_bog_roots run say SMOKE_ROOTS_OK")

    # 3. Cenote network: place a cenote feature and check it does not throw.
    out = rcon.command("place feature deepwaater:cenote 0 80 0")
    notice(f"place cenote: {out.strip()[:200]}")
    if any(word in out.lower() for word in ("failed", "unknown", "error")):
        fail(f"cenote placement failed: {out.strip()[:200]}")

    time.sleep(3)
    log_text = read_log()
    new_log = log_text[log_before:]
    if "SMOKE_TRUNK_OK" in new_log:
        notice("trunk of the swamp tree is placed (deepwaater:deep_bog_log)")
    else:
        fail("no deepwaater:deep_bog_log found at the tree trunk")
    if "SMOKE_ROOTS_OK" in new_log:
        notice("deep roots are placed (deepwaater:deep_bog_roots)")
    else:
        fail("no deepwaater:deep_bog_roots found below the tree")

    # 4. Any exception raised by our code during these commands.
    suspicious = [line for line in new_log.splitlines()
                  if ("Exception" in line or "Error" in line) and "deepwaater" in new_log]
    relevant = [line for line in new_log.splitlines()
                if "com.evizka" in line or "deepwaater" in line.lower()]
    if any("Exception" in line or "Error" in line for line in relevant):
        fail("exception from Deepwaater during world generation: "
             + " | ".join(line.strip() for line in relevant if "Exception" in line or "Error" in line)[:500])
    elif suspicious:
        notice(f"{len(suspicious)} unrelated error line(s) in log during the test")


def main():
    if not wait_for_server():
        fail("server did not finish starting within the timeout")
        return 1
    notice("server is up, connecting over RCON")
    try:
        rcon = Rcon(HOST, PORT, PASSWORD)
    except Exception as e:  # noqa: BLE001 - report any connection problem
        fail(f"could not connect over RCON: {e}")
        return 1
    try:
        run_checks(rcon)
    except Exception as e:  # noqa: BLE001
        fail(f"smoke test crashed: {e}")
    finally:
        try:
            rcon.command("stop")
        except Exception:  # noqa: BLE001 - the server may already be stopping
            pass
    if failures:
        print(f"Smoke test failed: {len(failures)} check(s)", flush=True)
        return 1
    notice("Smoke test passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
