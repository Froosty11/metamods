#!/usr/bin/env python3
"""Tiny RCON client: rcon.py <cmd...>  (localhost:25575, password metacraft-dev)."""
import socket, struct, sys, time

def pkt(i, t, body):
    b = body.encode() + b"\x00\x00"
    return struct.pack("<iii", len(b) + 8, i, t) + b

def recv(s):
    n = struct.unpack("<i", s.recv(4, socket.MSG_WAITALL))[0]
    d = s.recv(n, socket.MSG_WAITALL)
    return struct.unpack("<ii", d[:8]) + (d[8:-2].decode(errors="replace"),)

def run(cmd, host="127.0.0.1", port=25575, pw="metacraft-dev"):
    with socket.create_connection((host, port), timeout=30) as s:
        s.sendall(pkt(1, 3, pw)); recv(s)
        s.sendall(pkt(2, 2, cmd))
        return recv(s)[2]

if __name__ == "__main__":
    for attempt in range(3):
        try:
            print(run(" ".join(sys.argv[1:]))); break
        except OSError as e:
            if attempt == 2: raise
            time.sleep(2)
