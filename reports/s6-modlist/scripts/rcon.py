import socket,struct
class Rcon:
    def __init__(s,host="127.0.0.1",port=25575,pw="bench"):
        s.s=socket.create_connection((host,port),timeout=120); s.i=0
        s._send(3,pw); s._recv()
    def _send(s,t,body):
        s.i+=1; b=body.encode()+b"\0\0"; s.s.sendall(struct.pack("<iii",len(b)+8,s.i,t)+b)
    def _recv(s):
        def rd(n):
            d=b""
            while len(d)<n:
                c=s.s.recv(n-len(d))
                if not c: raise EOFError
                d+=c
            return d
        n=struct.unpack("<i",rd(4))[0]; d=rd(n); return d[8:-2].decode("utf-8","replace")
    def cmd(s,c):
        s._send(2,c); return s._recv()
