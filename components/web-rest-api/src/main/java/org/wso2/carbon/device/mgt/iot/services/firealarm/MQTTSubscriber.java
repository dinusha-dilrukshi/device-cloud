/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.device.mgt.iot.services.firealarm;

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;

import java.util.LinkedList;

public class MQTTSubscriber implements MqttCallback {

	private static Logger log = Logger.getLogger(MQTTSubscriber.class);

	private MqttClient client;
	private MqttConnectOptions options;
	private String clientId = "out:";
	private String subscribeTopic = "wso2/iot/+/FireAlarm/#";

	private MQTTSubscriber(String owner, String deviceUuid) {
		this.clientId += owner + ":" + deviceUuid;
		this.initSubscriber();
	}

	private void initSubscriber() {
		try {
			client = new MqttClient(FireAlarmControllerService.CONTROL_QUEUE_ENDPOINT, clientId, null);
			log.info("MQTT subscriber was created with ClientID : " + clientId);
		} catch (MqttException ex) {
			String errorMsg = "MQTT Client Error\n" + "\tReason:  " + ex.getReasonCode() +
					"\n\tMessage: " + ex.getMessage() + "\n\tLocalMsg: " +
					ex.getLocalizedMessage() + "\n\tCause: " + ex.getCause() +
					"\n\tException: " + ex;
			log.error(errorMsg);
		}

		options = new MqttConnectOptions();
		options.setCleanSession(false);
		options.setWill("fireAlarm/disconnection", "crashed".getBytes(), 2, true);
		client.setCallback(this);
	}

	/**
	 * @return the whether subscriber is connected to queue
	 */
	public boolean isConnected() {
		return client.isConnected();
	}

	public void subscribe() {
		try {
			client.connect(options);
			log.info("Subscriber connected to queue at: "
							 + FireAlarmControllerService.CONTROL_QUEUE_ENDPOINT);
		} catch (MqttSecurityException ex) {
			String errorMsg = "MQTT Security Exception when connecting to queue\n" + "\tReason:  " +
					ex.getReasonCode() + "\n\tMessage: " + ex.getMessage() +
					"\n\tLocalMsg: " + ex.getLocalizedMessage() + "\n\tCause: " +
					ex.getCause() + "\n\tException: " + ex;
			log.error(errorMsg);
		} catch (MqttException ex) {
			String errorMsg = "MQTT Exception when connecting to queue\n" + "\tReason:  " +
					ex.getReasonCode() + "\n\tMessage: " + ex.getMessage() +
					"\n\tLocalMsg: " + ex.getLocalizedMessage() + "\n\tCause: " +
					ex.getCause() + "\n\tException: " + ex;
			log.error(errorMsg);
		}

		if (client.isConnected()) {
			try {
				client.subscribe(subscribeTopic, 0);

				log.info("Subscribed with client id: " + clientId);
				log.info("Subscribed to topic: " + subscribeTopic);
			} catch (MqttException ex) {
				String errorMsg = "MQTT Exception when trying to subscribe to topic: " +
						subscribeTopic + "\n\tReason:  " + ex.getReasonCode() +
						"\n\tMessage: " + ex.getMessage() + "\n\tLocalMsg: " +
						ex.getLocalizedMessage() + "\n\tCause: " + ex.getCause() +
						"\n\tException: " + ex;
				log.error(errorMsg);
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.paho.client.mqttv3.MqttCallback#connectionLost(java.lang.
	 * Throwable)
	 */
	@Override
	public void connectionLost(Throwable arg0) {
		log.info("Lost Connection for client: " + this.clientId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.paho.client.mqttv3.MqttCallback#deliveryComplete(org.eclipse
	 * .paho.client.mqttv3.IMqttDeliveryToken)
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken arg0) {
		log.info("Message for client " + this.clientId + "delivered successfully.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang.
	 * String, org.eclipse.paho.client.mqttv3.MqttMessage)
	 */
	@Override
	public void messageArrived(final String arg0, final MqttMessage arg1) {
		Thread subscriberThread = new Thread() {

			public void run() {

				int lastIndex = arg0.lastIndexOf("/");
				String deviceId = arg0.substring(lastIndex + 1);

				lastIndex = arg1.toString().lastIndexOf(":");
				String msgContext = arg1.toString().substring(lastIndex + 1);

				LinkedList<String> deviceControlList = null;
				LinkedList<String> replyMessageList = null;

				if (msgContext.equals("IN")) {
					log.info("Recieved a control message: ");
					log.info("Control message topic: " + arg0);
					log.info("Control message: " + arg1.toString());
					synchronized (FireAlarmControllerService.internalControlsQueue) {
						deviceControlList = FireAlarmControllerService.internalControlsQueue.get(deviceId);
						if (deviceControlList == null) {
							FireAlarmControllerService.internalControlsQueue.put(deviceId,
																		  deviceControlList
																				  = new LinkedList<String>());
						}
					}
					deviceControlList.add(arg1.toString());
				} else if (msgContext.equals("OUT")) {
					log.info("Recieved reply from a device: ");
					log.info("Reply message topic: " + arg0);
					log.info("Reply message: " + arg1.toString().substring(0, lastIndex));
					synchronized (FireAlarmControllerService.replyMsgQueue) {
						replyMessageList = FireAlarmControllerService.replyMsgQueue.get(deviceId);
						if (replyMessageList == null) {
							FireAlarmControllerService.replyMsgQueue.put(deviceId, replyMessageList
																		  = new LinkedList<String>());
						}
					}
					replyMessageList.add(arg1.toString());
				}

			}
		};

		subscriberThread.start();

	}
}
