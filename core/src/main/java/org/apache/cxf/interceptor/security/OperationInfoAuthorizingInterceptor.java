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

package org.apache.cxf.interceptor.security;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.OperationInfo;

/**
 * 
 */
public class OperationInfoAuthorizingInterceptor extends SimpleAuthorizingInterceptor {
    private static final Logger LOG = LogUtils.getL7dLogger(OperationInfoAuthorizingInterceptor.class);

    @Override
    public void handleMessage(Message message) throws Fault {
        OperationInfo opinfo = getTargetOperationInfo(message);
        SecurityContext sc = message.get(SecurityContext.class);
        if (sc != null && sc.getUserPrincipal() != null) {
            if (opinfo.getName() != null
                && authorize(sc, opinfo.getName().getLocalPart())) {
                return;
            }
        } else if (!isMethodProtected(opinfo.getName().getLocalPart()) && isAllowAnonymousUsers()) {
            return;
        }
        
        throw new AccessDeniedException("Unauthorized");

    }

    protected boolean authorize(SecurityContext sc, String key) {
        List<String> expectedRoles = getExpectedRoles(key);
        if (expectedRoles.isEmpty()) {
            List<String> denyRoles = getDenyRoles(key);
            return denyRoles.isEmpty() ? true : isUserInRole(sc, denyRoles, true);
        }
        
        if (isUserInRole(sc, expectedRoles, false)) {
            return true;
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(sc.getUserPrincipal().getName() + " is not authorized");
        }
        return false;
    }

    protected OperationInfo getTargetOperationInfo(Message message) {
        BindingOperationInfo bop = message.getExchange().getBindingOperationInfo();
        if (bop != null) {
            return bop.getOperationInfo();
        }
        throw new AccessDeniedException("OperationInfo is not available : Unauthorized");
    }

    protected List<String> getExpectedRoles(String key) {
        List<String> roles = methodRolesMap.get(key);
        if (roles != null) {
            return roles;
        }
        return globalRoles;
    }

    protected List<String> getDenyRoles(String key) {
        return Collections.emptyList();    
    }
    
    protected boolean isMethodProtected(String key) {
        return !getExpectedRoles(key).isEmpty() || !getDenyRoles(key).isEmpty();
    }
}
