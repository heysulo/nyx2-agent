/*
 * The MIT License
 *
 * Copyright 2019 Team whileLOOP.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.whileloop.nyx2.n2a;

import com.whileloop.nyx2.messages.HeartBeatMessage;
import com.whileloop.nyx2.messages.LoginMessage;
import com.whileloop.nyx2.messages.LoginResponseMessage;
import com.whileloop.nyx2.messages.ServerStatusMessage;
import com.whileloop.nyx2.utils.NX2IntervalClock;
import com.whileloop.nyx2.utils.NX2Logger;
import com.whileloop.sendit.callbacks.SClientCallback;
import com.whileloop.sendit.client.SClient;
import com.whileloop.sendit.messages.SMessage;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/**
 *
 * @author sulochana
 */
public class N2Agent extends NX2Logger implements SClientCallback, NX2IntervalClock.NX2IntervalClockCallback {

    public static void main(String[] args) {
        N2Agent agent = new N2Agent(args[0]);
    }

    private SClient serverConnection;
    private final NioEventLoopGroup eventLoop;
    private final NioEventLoopGroup dedicatedTimerEventLoop;
    private String remoteAddress;
    private int remotePort = 3000;
    private NX2IntervalClock hbClock;

    public N2Agent(String remoteAddress) {
        this.eventLoop = new NioEventLoopGroup();
        this.dedicatedTimerEventLoop = new NioEventLoopGroup();
        this.remoteAddress = remoteAddress;

        setVerboseLevel(Loglevel.DEBUG);

        try {
            this.connectToServer();
        } catch (Exception ex) {
            crit("Failed to establish connection with the server: %s", ex.getMessage());
            this.shutdownAgent();
        }
    }

    private void connectToServer() throws SSLException, InterruptedException {
        debug("Connecting to: %s:%d", this.remoteAddress, this.remotePort);
        this.serverConnection = new SClient(this.remoteAddress, this.remotePort, eventLoop, this, false);
        info("Connected to: %s:%d", this.remoteAddress, this.remotePort);
    }

    private void shutdownAgent() {
        debug("Shutting down N2Agent");
        this.eventLoop.shutdownGracefully();
        debug("N2Agent shutdown complete.");
    }

    @Override
    public void OnConnect(SClient client) {
        debug("OnConnect");
    }

    @Override
    public void OnDisconnect(SClient client) {
        debug("OnDisconnect");
    }

    @Override
    public void OnMessage(SClient client, SMessage msg) {
        debug("SMessage Recieved: %s", msg.getClass().getName());
        if (msg instanceof ServerStatusMessage) {
            if (((ServerStatusMessage) msg).isReady()) {
                debug("Sending LoginMessage");
                client.Send(new LoginMessage());
            } else {
                crit("Remote Agent Service is not Ready. try again later");
                shutdownAgent();
                return;
            }
        } else if (msg instanceof LoginResponseMessage) {
            handleLoginResponse((LoginResponseMessage) msg);
        } else if (msg instanceof HeartBeatMessage) {
            handleHeartBeat((HeartBeatMessage)msg);
        }
    }

    @Override
    public void OnError(SClient client, Throwable cause) {
        debug("OnError");
        cause.printStackTrace();
    }

    @Override
    public void OnEvent(SClient client, Object event) {
        debug("OnEvent");
    }

    @Override
    public void OnSSLHandshakeSuccess(SClient client) {
        debug("Connected to server using CS: {@%s} PT: {@%s}", client.getCipherSuite(), client.getProtocol());
    }

    @Override
    public void OnSSLHandshakeFailure(SClient client) {
        debug("OnSSLHandshakeFailure");
    }

    @Override
    public void OnInterval(NX2IntervalClock clock) {
        if (clock == this.hbClock) {
            this.serverConnection.Send(new HeartBeatMessage());
        }
    }

    private void handleLoginResponse(LoginResponseMessage msg) {
        if (!msg.isAccepted()) {
            crit("Authentication Failure. Email Address and Password does not match");
            shutdownAgent();
            return;
        }
        
        info("Authentication Success!");
        this.hbClock = new NX2IntervalClock(this.eventLoop, this, 5, TimeUnit.SECONDS);
    }

    private void handleHeartBeat(HeartBeatMessage msg) {
        debug("RTT: %dms", (System.currentTimeMillis() - msg.getCreationTime()));
    }

}
