/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.connection;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.mongodb.MongoCredential;
import org.mongodb.MongoException;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.util.HashMap;
import java.util.Map;

import static org.mongodb.AuthenticationMechanism.GSSAPI;

class GSSAPIAuthenticator extends SaslAuthenticator {
    private static final String GSSAPI_MECHANISM_NAME = "GSSAPI";
    private static final String GSSAPI_OID = "1.2.840.113554.1.2.2";

    GSSAPIAuthenticator(final MongoCredential credential, final InternalConnection internalConnection,
                        final BufferProvider bufferProvider) {
        super(credential, internalConnection, bufferProvider);

        if (getCredential().getMechanism() != GSSAPI) {
            throw new MongoException("Incorrect mechanism: " + this.getCredential().getMechanism());
        }
    }

    @Override
    public String getMechanismName() {
        return GSSAPI_MECHANISM_NAME;
    }

    @Override
    protected SaslClient createSaslClient() {
        final MongoCredential credential = getCredential();
        try {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put(Sasl.CREDENTIALS, getGSSCredential(credential.getUserName()));

            SaslClient saslClient = Sasl.createSaslClient(new String[]{GSSAPI.getMechanismName()}, credential.getUserName(),
                    MONGODB_PROTOCOL, getInternalConnection().getServerAddress().getHost(), props, null);
            if (saslClient == null) {
                throw new MongoSecurityException(credential, String.format("No platform support for %s mechanism", GSSAPI));
            }

            return saslClient;
        } catch (SaslException e) {
            throw new MongoSecurityException(credential, "Exception initializing SASL client", e);
        } catch (GSSException e) {
            throw new MongoSecurityException(credential, "Exception initializing GSSAPI credentials", e);
        }
    }

    private GSSCredential getGSSCredential(final String userName) throws GSSException {
        Oid krb5Mechanism = new Oid(GSSAPI_OID);
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName(userName, GSSName.NT_USER_NAME);
        return manager.createCredential(name, GSSCredential.INDEFINITE_LIFETIME,
                krb5Mechanism, GSSCredential.INITIATE_ONLY);
    }
}
