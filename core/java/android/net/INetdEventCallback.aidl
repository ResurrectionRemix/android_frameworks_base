/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

/** {@hide} */
oneway interface INetdEventCallback {

    /**
     * Reports a single DNS lookup function call.
     * This method must not block or perform long-running operations.
     *
     * @param hostname the name that was looked up.
     * @param ipAddresses (possibly a subset of) the IP addresses returned.
     *        At most {@link #DNS_REPORTED_IP_ADDRESSES_LIMIT} addresses are logged.
     * @param ipAddressesCount the number of IP addresses returned. May be different from the length
     *        of ipAddresses if there were too many addresses to log.
     * @param timestamp the timestamp at which the query was reported by netd.
     * @param uid the UID of the application that performed the query.
     */
    void onDnsEvent(String hostname, in String[] ipAddresses, int ipAddressesCount, long timestamp,
            int uid);

    /**
     * Reports a single connect library call.
     * This method must not block or perform long-running operations.
     *
     * @param ipAddr destination IP address.
     * @param port destination port number.
     * @param timestamp the timestamp at which the call was reported by netd.
     * @param uid the UID of the application that performed the connection.
     */
    void onConnectEvent(String ipAddr, int port, long timestamp, int uid);
}
