# JavaNetworking
A simple Java TCP/IP client-server framework containing easy to customize message protocol with asynchronical read(both on client and server), and synchronical write(both client and server).
Why synchronized read? Using synchronical write is faster than offloading messages to worker thread on Android devices.

Allows for:
1.Creation of Server, which automatically handles incoming client connections and allows for easy processing of incoming client messages.
2.Creation of Client, which automatically manages incoming server messages and allows to send and receive customized messages to/from connected server.
