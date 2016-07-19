/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.core.clientcredentials;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.ca.mas.core.MobileSsoConfig;
import com.ca.mas.core.client.ServerClient;
import com.ca.mas.core.context.MssoContext;
import com.ca.mas.core.error.MAGErrorCode;
import com.ca.mas.core.http.MAGHttpClient;
import com.ca.mas.core.http.MAGRequest;
import com.ca.mas.core.http.MAGRequestBody;
import com.ca.mas.core.http.MAGResponse;
import com.ca.mas.core.http.MAGResponseBody;
import com.ca.mas.core.io.Charsets;
import com.ca.mas.core.io.IoUtils;
import com.ca.mas.core.token.ClientCredentials;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that encapsulates talking to the token server into Java method calls.
 * This handles just the network protocol for communicating with the MAG server to obtain dynamic client id and client credentials
 * It does not deal with state management, token persistence, looking up credentials in the context, or anything other
 * higher-level issue.
 */

public class ClientCredentialsClient extends ServerClient {

    public ClientCredentialsClient(MssoContext mssoContext) {
        super(mssoContext);
    }

    public ClientCredentials getClientCredentials(@NonNull String clientId,
                                                  String nonce,
                                                  @NonNull String deviceId) throws ClientCredentialsException, ClientCredentialsServerException {

        final URI tokenUri = conf.getTokenUri(MobileSsoConfig.PROP_TOKEN_URL_SUFFIX_CLIENT_CREDENTIALS);

        MAGRequest request = null;
        try {
            List<Pair<String, String>> form = new ArrayList<>();
            form.add(new Pair<String, String>(ServerClient.CLIENT_ID, clientId));
            form.add(new Pair<String, String>(NONCE, nonce));

            request = new MAGRequest.MAGRequestBuilder(tokenUri.toURL())
                    .header(DEVICE_ID, IoUtils.base64(deviceId, Charsets.ASCII))
                    .post(MAGRequestBody.urlEncodedFormBody(form))
                    .responseBody(MAGResponseBody.jsonBody())
                    .build();

        } catch (MalformedURLException e) {
            throw new ClientCredentialsException(MAGErrorCode.APPLICATION_INVALID, "Unable to set post for client credentials: " + e.getMessage(), e);
        }

        MAGHttpClient httpClient = mssoContext.getMAGHttpClient();
        final MAGResponse<JSONObject> response;
        try {
            response = httpClient.execute(request);

        } catch (IOException e) {
            throw new ClientCredentialsException(MAGErrorCode.APPLICATION_INVALID, "Unable to post to register_device: " + e.getMessage(), e);
        }

        try {
            final int statusCode = response.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw ServerClient.createServerException(response, ClientCredentialsServerException.class);
            }
            final JSONObject jsonObject = response.getBody().getContent();
            final String returnClientId = jsonObject.getString(CLIENT_ID);
            final String clientSecret = jsonObject.getString(CLIENT_SECRET);
            final Long clientExpiration = jsonObject.getLong(CLIENT_EXPIRATION);
            return new ClientCredentials(returnClientId, clientSecret, clientExpiration);

        } catch (JSONException e) {
            throw new ClientCredentialsException(MAGErrorCode.APPLICATION_INVALID, e);
        }

    }
}
