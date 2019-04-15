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
import com.whileloop.nyx2.messages.TerminationMessage;
import com.whileloop.nyx2.utils.NX2IntervalClock;
import com.whileloop.nyx2.utils.NX2Logger;
import com.whileloop.sendit.callbacks.SClientCallback;
import com.whileloop.sendit.client.SClient;
import com.whileloop.sendit.messages.SMessage;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.Console;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/**
 *
 * @author sulochana
 */
public class N2Agent extends NX2Logger implements SClientCallback, NX2IntervalClock.NX2IntervalClockCallback {

    public static void main(String[] args) {
        if (args.length != 1){
            System.err.println("Remote host address is not provided");
            System.exit(1);
        }
        N2Agent agent = new N2Agent(args[0]);
    }

    private SClient serverConnection;
    private final NioEventLoopGroup eventLoop;
    private String remoteAddress;
    private int remotePort = 3000;
    private NX2IntervalClock hbClock;
    private NX2IntervalClock reconClock;
    private boolean shutdownInprogress;

    public N2Agent(String remoteAddress) {
        this.eventLoop = new NioEventLoopGroup();
        this.remoteAddress = remoteAddress;
        this.shutdownInprogress = false;

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
        debug("Shutdown sequence initiated");
        this.shutdownInprogress = true;
        if (this.serverConnection != null){
            this.serverConnection.closeConnection();
        }
        debug("Shutting down Clocks");
        if (this.hbClock != null) {
            this.hbClock.stop();
        }
        
        if (this.reconClock != null) {
            this.reconClock.stop();
        }
        
        debug("Shutting down N2Agent");
        this.eventLoop.shutdownGracefully();
        debug("Shutdown sequence complete");
    }

    @Override
    public void OnConnect(SClient client) {
        debug("OnConnect");
        if (this.reconClock != null) {
            this.reconClock.stop();
        }
    }

    @Override
    public void OnDisconnect(SClient client) {
        debug("OnDisconnect");
        if (this.hbClock != null) {
            this.hbClock.stop();
        }
        
        if (shutdownInprogress) {
            debug("Disconnection procedures skipped due to shutdown sequence");
            return;
        }
        
        debug("Creating Reconnecting Clock");
        this.reconClock = new NX2IntervalClock(eventLoop, this, 5, TimeUnit.SECONDS);
    }

    @Override
    public void OnMessage(SClient client, SMessage msg) {
        debug("SMessage Recieved: %s", msg.getClass().getName());
        if (msg instanceof ServerStatusMessage) {
            if (((ServerStatusMessage) msg).isReady()) {
                loginProcess();
            } else {
                crit("Remote Agent Service is not Ready. try again later");
                shutdownAgent();
                return;
            }
        } else if (msg instanceof LoginResponseMessage) {
            handleLoginResponse((LoginResponseMessage) msg);
        } else if (msg instanceof HeartBeatMessage) {
            handleHeartBeat((HeartBeatMessage)msg);
        } else if (msg instanceof TerminationMessage) {
            handleTerminationMessage((TerminationMessage)msg);
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
        } else if (clock == this.reconClock) {
            try {
                this.connectToServer();
            } catch (Exception ex) {
                crit("Reconnection attempt failed: %s", ex.getMessage());
            }
        }
    }

    private void handleLoginResponse(LoginResponseMessage msg) {
        if (!msg.isAccepted()) {
            crit("Authentication Failure. Email Address and Password does not match");
            shutdownAgent();
            return;
        }
        
        info("Authentication Success!");
        if (this.hbClock != null){
            this.hbClock.stop();
        }
        ConfigurationStore.setConfiguration(ConfigurationStore.ConfigKey.AUTH_TOKEN, msg.getAuthToken());
        this.hbClock = new NX2IntervalClock(this.eventLoop, this, 1, TimeUnit.SECONDS);
    }

    private void handleHeartBeat(HeartBeatMessage msg) {
        debug("RTT: %dms", (System.currentTimeMillis() - msg.getCreationTime()));
    }
    
    private void loginProcess() {
        LoginMessage msg = new LoginMessage();
        String authToken = ConfigurationStore.getConfiguration(ConfigurationStore.ConfigKey.AUTH_TOKEN);
        if (authToken != null) {
            msg.setMechanism(LoginMessage.LoginMechanism.AUTH_TOKEN);
            msg.setAuthToken(authToken);
            debug("Authenticating using AuthToken");
            this.serverConnection.Send(msg);
            return;
        } 
        
        Console console = System.console();
        if (console != null) {
            System.out.println("+------------------- Authentication Required -------------------+\n\n");
            msg.setEmail(console.readLine("Email Address: "));
            char[] pwd = console.readPassword("Password: ");
            msg.setPassword(new String(pwd));
            System.out.println("\n\n+---------------------------------------------------------------+");
            msg.setMechanism(LoginMessage.LoginMechanism.CREDENTIALS);
        } else {
            System.err.println("Unable to access System Console.");
            shutdownAgent();
        }
        debug("Authenticating using Credentials");
        this.serverConnection.Send(msg);
    }

    private void handleTerminationMessage(TerminationMessage terminationMessage) {
        crit("Termination request recieved from N2CC: %s", terminationMessage.getDescription());
        shutdownAgent();        
    }

}
