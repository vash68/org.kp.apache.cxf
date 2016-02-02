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

package org.apache.cxf.binding.soap.interceptor;

import java.util.Iterator;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;

public class SoapHeaderOutFilterInterceptor extends AbstractSoapInterceptor {
    public static final SoapHeaderOutFilterInterceptor INSTANCE = new SoapHeaderOutFilterInterceptor();
    
    public SoapHeaderOutFilterInterceptor()  {
        super(Phase.PRE_LOGICAL);
    }

    public void handleMessage(SoapMessage message) throws Fault {
        Iterator<Header> iter =  message.getHeaders().iterator();
        
        while (iter.hasNext()) {
            Header hdr  = iter.next();
            //Only remove inbound marked headers..
            if (hdr == null || hdr.getDirection() == Header.Direction.DIRECTION_IN) {
                iter.remove(); 
            }
        }
    }

}
