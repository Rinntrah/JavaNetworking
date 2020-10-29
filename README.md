# JavaNetworking
A simple Java TCP/IP client-server library containing easy to customize message protocol with asynchronical read(both on client and server), and synchronical write(both client and server).<br>
This library was meant to be used on Android devices, thus I've decided to use synchronical write as it is faster than offloading messages to worker thread which requires synchronizing it's state with latches and Thread.sleep().<br>
Allows for:<br>
1.Creation of Server, which automatically handles incoming client connections and allows for easy processing of incoming client messages.<br>
2.Creation of Client, which automatically manages incoming server messages and allows to send and receive customized messages to/from connected server.<br>
