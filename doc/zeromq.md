# Notes on zeromq

## Basics
- context
  - need to be destroied
- socket
  - need to be closed
  - Socket bind context to the port
    - for example
      ```C
      void * requester = zmq_socket(context, ZMQ_REQ);
      ```

  - Socket types, * indicate the types that are used or might be used in this project at this point:


    | Socket type | interest |
    |:------------|:---------|
    |ZMQ\_PAIR    ||
    |ZMQ\_PUB     |\*for sure|
    |ZMQ\_SUB     |\*for sure|
    |ZMQ\_REQ     |\*for sure|
    |ZMQ\_REP     |\*for sure|
    |ZMQ\_DEALER  ||
    |ZMQ\_ROUTER  ||
    |ZMQ\_PULL    |Maybe?|
    |ZMQ\_PUSH    |Maybe?|
    |ZMQ\_XPUB    |Maybe?|
    |ZMQ\_XSUB    |Maybe?|
    |ZMQ\_STREAM  ||

- zmq\_msg
  - zmq\_msg\_t
  - zmq\_msg\_data
  - zmq\_msg\_send
  - zmq\_msg\_recv
  - zmq\_msg\_init\_size
  - zmq\_msg\_close

- server
  - bind:
    ```C
    zmq_bind(<responder socket>, "tcp://*:<port number>");
    ```

- client
  - connect:
    ```C
    zmq_connect(<requester socket>, "tcp://<server>:<port>");
    ```
- Jeromq:
  - [ZMQ.Context](https://github.com/zeromq/jeromq/blob/master/src/main/java/org/zeromq/ZMQ.java) vs [zeromq.ZContext](https://github.com/zeromq/jeromq/blob/master/src/main/java/org/zeromq/ZContext.java):
    - ZMQ.Context contains:
      - [zmq.Ctx](https://github.com/zeromq/jeromq/blob/master/src/main/java/zmq/Ctx.java)
    - zeromq.ZContext contains:
      - **ZMQ.Context**
      - int ioThreads
      - List<Socket> sockets
      - Lock mutex
      - Others ...
      - So ZContext maybe thread safe?
    - Sockets are not thread-safe, but zmq.ctx and ZMQ.Context (thin wrapper) are. ([source](https://github.com/zeromq/jeromq/wiki/Sharing-ZContext-between-thread))
    - ZContext is not thread-safe.
