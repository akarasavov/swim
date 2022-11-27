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

import atk.app.network.NetworkRequest;
import atk.app.network.TcpRequest;
import atk.app.util.channel.WriteableChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);
    private final WriteableChannel<TcpRequest> requestsChannel;

    public NettyServerHandler(WriteableChannel<TcpRequest> requestsChannel) {
        this.requestsChannel = requestsChannel;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        logger.info("New client connected {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        logger.info("Client disconnected {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logger.info("Received {} from {}", msg, ctx.channel().remoteAddress());
        if (msg instanceof NetworkRequest) {
            var swimRequest = new TcpRequest((NetworkRequest) msg);
            swimRequest.getResponseHandler().whenComplete((response, throwable) -> {
                if (response != null) {
                    logger.info("Message send {}", msg);
                    ctx.writeAndFlush(response);
                } else {
                    logger.warn("Didn't send response for {}", swimRequest, throwable);
                }
            });
            requestsChannel.push(swimRequest);
        } else {
            throw new IllegalStateException("Doesn't support message " + msg);
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
}
