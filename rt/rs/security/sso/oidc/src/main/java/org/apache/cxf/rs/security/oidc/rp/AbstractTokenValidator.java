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
package org.apache.cxf.rs.security.oidc.rp;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.rs.security.jose.jwk.JsonWebKey;
import org.apache.cxf.rs.security.jose.jwk.JsonWebKeys;
import org.apache.cxf.rs.security.jose.jwk.JwkUtils;
import org.apache.cxf.rs.security.jose.jws.JwsSignatureVerifier;
import org.apache.cxf.rs.security.jose.jws.JwsUtils;
import org.apache.cxf.rs.security.jose.jwt.JwtClaims;
import org.apache.cxf.rs.security.jose.jwt.JwtToken;
import org.apache.cxf.rs.security.jose.jwt.JwtUtils;
import org.apache.cxf.rs.security.oauth2.provider.AbstractOAuthJoseJwtConsumer;

public abstract class AbstractTokenValidator extends AbstractOAuthJoseJwtConsumer {
    private static final String SELF_ISSUED_ISSUER = "https://self-issued.me";
    private String issuerId;
    private int clockOffset;
    private int ttl;
    private WebClient jwkSetClient;
    private boolean supportSelfIssuedProvider;
    private boolean strictTimeValidation;
    private ConcurrentHashMap<String, JsonWebKey> keyMap = new ConcurrentHashMap<String, JsonWebKey>(); 

    /**
     * Validate core JWT claims
     * @param claims the claims
     * @param clientId OAuth2 client id
     * @param validateClaimsAlways if set to true then enforce that the claims 
     *                             to be validated must be set
     */
    protected void validateJwtClaims(JwtClaims claims, String clientId, boolean validateClaimsAlways) {
        // validate the issuer
        String issuer = claims.getIssuer();
        if (issuer == null && validateClaimsAlways) {
            throw new SecurityException("Invalid provider");
        }
        if (supportSelfIssuedProvider && issuerId == null 
            && issuer != null && SELF_ISSUED_ISSUER.equals(issuer)) {
            //TODO: self-issued provider token validation
        } else {
            if (issuer != null && !issuer.equals(issuerId)) {
                throw new SecurityException("Invalid provider");
            }
            // validate subject
            if (claims.getSubject() == null) {
                throw new SecurityException("Invalid subject");
            }
            // validate audience
            String aud = claims.getAudience();
            if (aud == null && validateClaimsAlways || aud != null && !clientId.equals(aud)) {
                throw new SecurityException("Invalid audience");
            }
    
            // If strict time validation: if no issuedTime claim is set then an expiresAt claim must be set
            // Otherwise: validate only if expiresAt claim is set
            boolean expiredRequired = 
                validateClaimsAlways || strictTimeValidation && claims.getIssuedAt() == null;
            JwtUtils.validateJwtExpiry(claims, clockOffset, expiredRequired);
            
            // If strict time validation: If no expiresAt claim is set then an issuedAt claim must be set
            // Otherwise: validate only if issuedAt claim is set
            boolean issuedAtRequired = 
                validateClaimsAlways || strictTimeValidation && claims.getExpiryTime() == null;
            JwtUtils.validateJwtIssuedAt(claims, ttl, clockOffset, issuedAtRequired);
            
            if (strictTimeValidation) {
                JwtUtils.validateJwtNotBefore(claims, clockOffset, strictTimeValidation);
            }
        }
    }
    
    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public void setJwkSetClient(WebClient jwkSetClient) {
        this.jwkSetClient = jwkSetClient;
    }

    @Override
    protected JwsSignatureVerifier getInitializedSignatureVerifier(JwtToken jwt) {
        JsonWebKey key = null;
        if (supportSelfIssuedProvider && SELF_ISSUED_ISSUER.equals(jwt.getClaim("issuer"))) {
            String publicKeyJson = (String)jwt.getClaim("sub_jwk");
            if (publicKeyJson != null) {
                JsonWebKey publicKey = JwkUtils.readJwkKey(publicKeyJson);
                String thumbprint = JwkUtils.getThumbprint(publicKey);
                if (thumbprint.equals(jwt.getClaim("sub"))) {
                    key = publicKey;
                }
            }
            if (key == null) {
                throw new SecurityException("Self-issued JWK key is invalid or not available");
            }
        } else {
            String keyId = jwt.getHeaders().getKeyId();
            key = keyId != null ? keyMap.get(keyId) : null;
            if (key == null && jwkSetClient != null) {
                JsonWebKeys keys = jwkSetClient.get(JsonWebKeys.class);
                if (keyId != null) {
                    key = keys.getKey(keyId);
                } else if (keys.getKeys().size() == 1) {
                    key = keys.getKeys().get(0);
                }
                keyMap.putAll(keys.getKeyIdMap());
            }
        }
        JwsSignatureVerifier theJwsVerifier = null;
        if (key != null) {
            theJwsVerifier = JwsUtils.getSignatureVerifier(key);
        } else {
            theJwsVerifier = super.getInitializedSignatureVerifier(jwt);
        }
        if (theJwsVerifier == null) {
            throw new SecurityException("JWS Verifier is not available");
        }
        
        return theJwsVerifier;
    }

    public void setSupportSelfIssuedProvider(boolean supportSelfIssuedProvider) {
        this.supportSelfIssuedProvider = supportSelfIssuedProvider;
    }

    public int getClockOffset() {
        return clockOffset;
    }

    public void setClockOffset(int clockOffset) {
        this.clockOffset = clockOffset;
    }

    public void setStrictTimeValidation(boolean strictTimeValidation) {
        this.strictTimeValidation = strictTimeValidation;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
