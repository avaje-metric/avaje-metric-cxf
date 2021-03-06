/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.avaje.metric.cxf;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.avaje.metric.TimedMetricGroup;

public class ResponseTimeMessageOutInterceptor extends AbstractMessageResponseTimeInterceptor {

  private EndingInterceptor ending = new EndingInterceptor();

  public ResponseTimeMessageOutInterceptor(TimedMetricGroup timedMetricGroup) {
    super(Phase.SEND, timedMetricGroup);
  }

  public void handleMessage(Message message) throws Fault {
    Exchange ex = message.getExchange();
    if (Boolean.TRUE.equals(message.get(Message.PARTIAL_RESPONSE_MESSAGE))) {
      return;
    }
    if (isClient(message)) {
      if (ex.isOneWay()) {
        message.getInterceptorChain().add(ending);
      }
      beginHandlingMessage(ex, message);
    } else { // the message is handled by server
      endHandlingMessage(ex, message);
    }
  }

  @Override
  public void handleFault(Message message) {
    Exchange ex = message.getExchange();
    if (ex.isOneWay()) {
      // do nothing, done by the ResponseTimeInvokerInterceptor
    } else {
      endHandlingMessageWithFault(ex, message);
    }
  }

  EndingInterceptor getEndingInterceptor() {
    return ending;
  }

  public class EndingInterceptor extends AbstractPhaseInterceptor<Message> {
    public EndingInterceptor() {
      super(Phase.PREPARE_SEND_ENDING);
    }

    public void handleMessage(Message message) throws Fault {
      endHandlingMessage(message.getExchange(), message);
    }

    public void handleFault(Message message) throws Fault {
      endHandlingMessage(message.getExchange(), message);
    }
  }
}
