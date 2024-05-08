from socket import *


port = 12000
s_soc = socket(AF_INET, SOCK_STREAM)
s_soc.bind(('localhost', port))
s_soc.listen(1)
ServerName = "Universal Server"
print(ServerName + " is now ONLINE")

while True:

        con, addr = s_soc.accept()
        data = con.recv(1024).decode()

        resp = data.upper()
        con.send(resp.encode())
        print("Sent")
        con.close()
