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

package org.wso2.carbon.device.mgt.iot.devicecontroller.impl;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.wso2.carbon.device.mgt.common.DeviceManagementException;
import org.wso2.carbon.device.mgt.iot.config.FireAlarmConfigurationManager;
import org.wso2.carbon.device.mgt.iot.config.FireAlarmManagementConfig;
import org.wso2.carbon.device.mgt.iot.config.FireAlarmManagementControllerConfig;
import org.wso2.carbon.device.mgt.iot.config.controlqueue.FireAlarmControlQueueConfig;
import org.wso2.carbon.device.mgt.iot.devicecontroller.ControlQueueConnector;

import java.util.HashMap;

// TODO: Auto-generated Javadoc

/**
 * The Class MQTTControlQueue. It is an implementation of the interface
 * ControlQueueConnector.
 * This implementation supports publishing of control signals received to an
 * MQTT end-point.
 * The configuration settings for the MQTT end-point are read from the
 * 'controller.xml' file of the project.
 * This is done using the class 'DefaultDeviceControlConfigs.java' which loads
 * the settings from the default xml configs file -
 * /resources/conf/device-controls/controller.xml
 *
 * @author smean-MAC
 */
public class MQTTControlQueue implements ControlQueueConnector, MqttCallback {

	/** The logger for this class. */
	Logger log = Logger.getLogger(MQTTControlQueue.class);

	/** The control queue (mqtt) endpoint to publish control messages. */
	private String CONTROL_QUEUE_ENDPOINT = "";

	/** The control queue username. */
	private String CONTROL_QUEUE_USERNAME = "";

	/** The control queue password. */
	private String CONTROL_QUEUE_PASSWORD = "";

	/** The HTTP reply token. */
	private String httpReply = "%d - %s \n%s";

	/**
	 * Instantiates a new MQTT control queue.
	 */
	public MQTTControlQueue() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.wso2.iot.device.controller.ControlQueueConnector#initControlQueue()
	 */
	@Override
	public String initControlQueue() {
		String controlQueue = null;
		String mqttUrl = "";
		String mqttPort = "";

		FireAlarmManagementConfig config = null;

		try {
			config = FireAlarmConfigurationManager.getInstance().getFireAlarmMgtConfig();

			// controller configurations
			FireAlarmManagementControllerConfig controllerConfig = config
					.getFireAlarmManagementControllerConfig();

			controlQueue = controllerConfig.getDeviceControlQueue();

			FireAlarmControlQueueConfig controlQueueConfig = config.getControlQueuesMap().get(
					controlQueue);

			mqttUrl = controlQueueConfig.getEndPoint();
			mqttPort = controlQueueConfig.getPort();

			CONTROL_QUEUE_ENDPOINT = mqttUrl + ":" + mqttPort;
			CONTROL_QUEUE_USERNAME = controlQueueConfig.getUserName();
			CONTROL_QUEUE_PASSWORD = controlQueueConfig.getPassword();

			log.info("CONTROL_QUEUE_ENDPOINT : " + CONTROL_QUEUE_ENDPOINT);
			return String.format(httpReply, HttpStatus.SC_OK, HttpStatus.getStatusText(
					HttpStatus.SC_OK), "");
		} catch (DeviceManagementException ex) {
			log.error("Error occurred when trying to read configurations file: firealarm-config"
							  + ".xml", ex);
			return String.format(httpReply, HttpStatus.SC_INTERNAL_SERVER_ERROR,
								 HttpStatus.getStatusText(HttpStatus.SC_INTERNAL_SERVER_ERROR), ex);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.wso2.iot.device.controller.ControlQueueConnector#enqueueControls(
	 * java.util.HashMap)
	 */
	@Override
	public String enqueueControls(HashMap<String, String> deviceControls) {

		MqttClient client;
		MqttConnectOptions options;

		String owner = deviceControls.get("owner");
		String deviceType = deviceControls.get("deviceType");
		String deviceId = deviceControls.get("deviceId");
		String key = deviceControls.get("key");
		String value = deviceControls.get("value");

		String clientId = owner + "." + deviceId;

		if (clientId.length() > 24) {
			String errorString = "No of characters '" + clientId.length() + "' for ClientID: '" +
					clientId +
					"' is invalid (should be less than 24, hence please provide a simple 'owner' tag)";
			log.error(errorString);
			return String.format(httpReply, HttpStatus.SC_NOT_ACCEPTABLE, HttpStatus.getStatusText(
					HttpStatus.SC_NOT_ACCEPTABLE), errorString);
		} else {
			log.info("No of Characters in ClientID : '" + clientId + "' is " + clientId.length());
		}

		String publishTopic = "wso2/iot/" + owner + "/" + deviceType + "/" + deviceId;
		String payLoad = key + ":" + value;

		try {
			client = new MqttClient(CONTROL_QUEUE_ENDPOINT, clientId);
			options = new MqttConnectOptions();
			options.setWill("iotDevice/clienterrors", "crashed".getBytes(), 2, true);
			client.setCallback(this);
			client.connect(options);

			log.info("MQTT Client successfully connected to: " + CONTROL_QUEUE_ENDPOINT +
							 ", with client ID - " + clientId);

			MqttMessage message = new MqttMessage();
			message.setPayload(payLoad.getBytes());
			client.publish(publishTopic, payLoad.getBytes(), 0, true);

			log.info("MQTT Client successfully published to topic: " + publishTopic +
							 ", with payload - " + payLoad);

			client.disconnect();

			log.info("MQTT Client disconnected from MQTT broker");
		} catch (MqttException me) {
			log.error("MQTT Client Error");
			log.error("Reason:  " + me.getReasonCode());
			log.error("Message: " + me.getMessage());
			log.error("LocalMsg: " + me.getLocalizedMessage());
			log.error("Cause: " + me.getCause());
			log.error("Exception: " + me);
			me.printStackTrace();
			return String.format(httpReply, HttpStatus.SC_INTERNAL_SERVER_ERROR,
								 HttpStatus.getStatusText(HttpStatus.SC_INTERNAL_SERVER_ERROR), me);
		}
		return String.format(httpReply, HttpStatus.SC_ACCEPTED, HttpStatus.getStatusText(
				HttpStatus.SC_ACCEPTED), "");
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
		log.error("Connection to MQTT Endpoint Lost");
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
		log.info("Published topic: '" + arg0.getTopics()[0] + "' successfully to client: '" +
						 arg0.getClient().getClientId() + "'");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang.
	 * String, org.eclipse.paho.client.mqttv3.MqttMessage)
	 */
	@Override
	public void messageArrived(String arg0, MqttMessage arg1) throws Exception {
		log.info("MQTT Message recieved: " + arg1.toString());
	}

	// public static void main(String[] args) {
	//
	// HashMap<String, String> myMap = new HashMap<String, String>();
	// myMap.put("deviceType", "Arduino");
	// myMap.put("owner", "Smeansbeer");
	// myMap.put("macAddress", "123456");
	// myMap.put("key", "TempSensor");
	// myMap.put("value", "123");
	//
	// MQTTControlQueue newInst = new MQTTControlQueue();
	// System.out.println(newInst.initControlQueue());
	// System.out.println(newInst.enqueueControls(myMap));
	// }
}
