/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.foundation;

import android.content.Context;

import com.ca.mas.foundation.auth.MASAuthenticationProviders;

/**
 * Interface that will receive various notifications and requests for MAG Client.
 */
public interface MASUserLoginWithUserCredentialsListener {

    /**
     * Notify the host application that a request to authenticate is triggered by the MAG authentication process.
     */
    void onAuthenticateRequest(Context context, long requestId, MASAuthenticationProviders providers);


}
