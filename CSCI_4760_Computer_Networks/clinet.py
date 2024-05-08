from socket import *
s_name, s_port = 'localhost', 12000

c_soc = socket(AF_INET, SOCK_STREAM)
c_soc.connect((s_name, s_port))
print('Connected to Remote Server')

data = input("input lowercase sentence ")
c_soc.send(data.encode())
print('Data sent... waiting for the response.')

resp = c_soc.recv(1024)
print('From Server', resp.decode())
c_soc.close()

