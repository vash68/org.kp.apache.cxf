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
package org.apache.cxf.rs.security.oauth2.grants.jwt;

import java.util.List;
import java.util.Set;

import org.apache.cxf.rs.security.jose.jws.JwsHeaders;
import org.apache.cxf.rs.security.jose.jws.JwsSignatureVerifier;
import org.apache.cxf.rs.security.jose.jws.JwsUtils;
import org.apache.cxf.rs.security.jose.jwt.JwtClaims;
import org.apache.cxf.rs.security.jose.jwt.JwtUtils;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.grants.AbstractGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;


/**
 * The "JWT Bearer" grant handler
 */
public abstract class AbstractJwtHandler extends AbstractGrantHandler {
    private Set<String> supportedIssuers; 
    private JwsSignatureVerifier jwsVerifier;
    private int ttl;
    private int clockOffset;
        
    protected AbstractJwtHandler(List<String> grants) {
        super(grants);
    }
    
    protected void validateSignature(JwsHeaders headers, String unsignedText, byte[] signature) {
        JwsSignatureVerifier theSigVerifier = getInitializedSigVerifier();
        if (!theSigVerifier.verify(headers, unsignedText, signature)) {    
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }
    }
    
    protected void validateClaims(Client client, JwtClaims claims) {
        validateIssuer(claims.getIssuer());
        validateSubject(client, claims.getSubject());
        validateAudience(client, claims.getAudience());
        
        // If we have no issued time then we need to have an expiry
        boolean expiredRequired = claims.getIssuedAt() == null;
        JwtUtils.validateJwtExpiry(claims, clockOffset, expiredRequired);
        
        JwtUtils.validateJwtNotBefore(claims, clockOffset, false);
        
        // If we have no expiry then we must have an issued at
        boolean issuedAtRequired = claims.getExpiryTime() == null;
        JwtUtils.validateJwtIssuedAt(claims, ttl, clockOffset, issuedAtRequired);
    }

    protected void validateIssuer(String issuer) {
        if (issuer == null || !supportedIssuers.contains(issuer)) {
            throw new OAuthServiceException(OAuthConstants.INVALID_GRANT);
        }
    }
    
    protected void validateSubject(Client client, String subject) {
        //TODO
    }
    protected void validateAudience(Client client, String audience) {
        //TODO
    }
    public void setSupportedIssuers(Set<String> supportedIssuers) {
        this.supportedIssuers = supportedIssuers;
    }

    public void setJwsVerifier(JwsSignatureVerifier jwsVerifier) {
        this.jwsVerifier = jwsVerifier;
    }
    protected JwsSignatureVerifier getInitializedSigVerifier() {
        if (jwsVerifier != null) {
            return jwsVerifier;    
        } 
        return JwsUtils.loadSignatureVerifier(true);
    }
    
    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public int getClockOffset() {
        return clockOffset;
    }

    public void setClockOffset(int clockOffset) {
        this.clockOffset = clockOffset;
    }
}
