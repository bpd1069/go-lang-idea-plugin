/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.dlv;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.json.JsonObjectDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.*;
import org.jetbrains.io.ChannelBufferToString;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;
import org.jetbrains.jsonProtocol.Request;

import java.io.IOException;

public class DlvVm extends VmBase {
  final static Logger LOG = Logger.getInstance(DlvVm.class);

  @NotNull private final DlvCommandProcessor commandProcessor;
  @NotNull private final StandaloneVmHelper vmHelper;
  @NotNull private final DummyBreakpointManager breakpointManager = new DummyBreakpointManager();

  public DlvVm(@NotNull DebugEventListener tabListener, @NotNull Channel channel) {
    super(tabListener);

    commandProcessor = new DlvCommandProcessor() {
      @Override
      public boolean write(@NotNull Request message) throws IOException {
        return vmHelper.fun(message);
      }
    };

    vmHelper = new StandaloneVmHelper(this, commandProcessor) {
      @Override
      public boolean fun(@NotNull Request message) {
        ByteBuf content = message.getBuffer();
        LOG.info("OUT: " + content.toString(CharsetToolkit.UTF8_CHARSET));
        return write(content);
      }
    };
    vmHelper.setChannel(channel);

    channel.pipeline().addLast(new JsonObjectDecoder(), new SimpleChannelInboundHandlerAdapter() {
      @Override
      protected void messageReceived(ChannelHandlerContext context, Object message) throws Exception {
        if (message instanceof ByteBuf) {
          LOG.info("IN: " + ((ByteBuf)message).toString(CharsetToolkit.UTF8_CHARSET));
          CharSequence string = ChannelBufferToString.readChars((ByteBuf)message);
          JsonReaderEx ex = new JsonReaderEx(string);
          getCommandProcessor().getMessageManager().processIncoming(ex);
        }
      }
    });
  }

  @NotNull
  @Override
  public AttachStateManager getAttachStateManager() {
    return vmHelper;
  }

  @NotNull
  public final DlvCommandProcessor getCommandProcessor() {
    return commandProcessor;
  }

  @NotNull
  @Override
  public ScriptManagerBase<ScriptBase> getScriptManager() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public BreakpointManager getBreakpointManager() {
    return breakpointManager;
  }

  @NotNull
  @Override
  public SuspendContextManagerBase<SuspendContextBase, CallFrame> getSuspendContextManager() {
    return new SuspendContextManagerBase<SuspendContextBase, CallFrame>() {
      @NotNull
      @Override
      public Promise<Void> continueVm(@NotNull StepAction stepAction, int stepCount) {
        return Promise.DONE;
      }

      @Override
      protected DebugEventListener getDebugListener() {
        return DlvVm.this.getDebugListener();
      }

      @NotNull
      @Override
      protected Promise<?> doSuspend() {
        return Promise.DONE;
      }
    };
  }
}