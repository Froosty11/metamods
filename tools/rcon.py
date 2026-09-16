#!/usr/bin/env python3
"""Minimal Minecraft RCON client: python3 rcon.py 'cmd1' 'cmd2' ..."""
import socket, struct, sys

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "spike"


def packet(req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode() + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def read(sock):
    length = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode(errors="replace")


def main():
    s = socket.create_connection((HOST, PORT), timeout=10)
    s.sendall(packet(1, 3, PASSWORD))
    rid, _, _ = read(s)
    if rid == -1:
        sys.exit("rcon auth failed")
    for i, cmd in enumerate(sys.argv[1:], start=2):
        s.sendall(packet(i, 2, cmd))
        _, _, body = read(s)
        print(f"> {cmd}\n{body}")
    s.close()


if __name__ == "__main__":
    main()
