/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package atk.app.network.netty;

import atk.app.network.NetworkResponse;
import atk.app.util.channel.Channel;
import atk.app.util.channel.BoundedChannel;
import atk.app.util.channel.ReadableChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.Closeable;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyClientHandler extends ChannelInboundHandlerAdapter implements Closeable {

    private final Channel<NetworkResponse> responseHandler;
    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    public NettyClientHandler() {
        this.responseHandler = new BoundedChannel<>(1);
    }

    public ReadableChannel<NetworkResponse> getResponseHandler() {
        return responseHandler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logger.info("Message received {}", msg);
        if (msg instanceof NetworkResponse) {
            responseHandler.push((NetworkResponse) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void close() throws IOException {
        responseHandler.close();
    }
}
