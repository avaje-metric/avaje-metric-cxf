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

import javax.xml.namespace.QName;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.FaultMode;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.avaje.metric.TimedMetricEvent;
import org.avaje.metric.TimedMetricGroup;

public abstract class AbstractMessageResponseTimeInterceptor extends AbstractPhaseInterceptor<Message> {


  protected final TimedMetricGroup timedMetricGroup;

  protected AbstractMessageResponseTimeInterceptor(String phase, TimedMetricGroup timedMetricGroup) {
    super(phase);
    this.timedMetricGroup = timedMetricGroup;
  }

  protected boolean isClient(Message msg) {
    return msg == null ? false : Boolean.TRUE.equals(msg.get(Message.REQUESTOR_ROLE));
  }

  protected void setFault(Message message, Exchange ex) {
    FaultMode mode = message.get(FaultMode.class);
    if (mode == null) {
      mode = FaultMode.RUNTIME_FAULT;
    }
    ex.put(FaultMode.class, mode);
  }

  protected void beginHandlingMessage(Exchange ex, Message message) {

    if (message == null || ex == null) {
      return;
    }

    QName opName = (QName) message.getContextualProperty(Message.WSDL_OPERATION);


    TimedMetricEvent startEvent = timedMetricGroup.start(opName.getLocalPart());
    
    ex.put(TimedMetricEvent.class, startEvent);
  }

  protected void endHandlingMessageWithFault(Exchange ex, Message message) {
    endHandlingMessage(true, ex, message);
  }

  protected void endHandlingMessage(Exchange ex, Message message) {
    endHandlingMessage(false, ex, message);
  }

  protected void endHandlingMessage(boolean isFault, Exchange ex, Message message) {

    if (ex == null) {
      return;
    }

    TimedMetricEvent timedMetricEvent = ex.get(TimedMetricEvent.class);
    if (timedMetricEvent == null) {
      return;
    }

    if (isFault || isFault(ex, message)) {
      timedMetricEvent.endWithError();
    } else {
      timedMetricEvent.endWithSuccess();
    }

  }

  private boolean isFault(Exchange ex, Message message) {
    return (message == ex.getInFaultMessage() || message == ex.getOutFaultMessage());
  }

}
