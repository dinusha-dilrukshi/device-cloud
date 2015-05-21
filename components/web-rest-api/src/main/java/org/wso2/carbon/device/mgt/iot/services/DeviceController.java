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

package org.wso2.carbon.device.mgt.iot.services;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.wso2.carbon.device.mgt.iot.devicecontroller.ControlQueueConnector;
import org.wso2.carbon.device.mgt.iot.devicecontroller.DataStoreConnector;
import org.wso2.carbon.device.mgt.iot.utils.DefaultDeviceControlConfigs;
import org.wso2.carbon.device.mgt.iot.utils.IoTConfiguration;
import org.wso2.carbon.device.mgt.iot.utils.ResourceFileLoader;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.io.File;
import java.util.HashMap;

@Path(value = "/DeviceController")
public class DeviceController {

	private static Logger log = Logger.getLogger(DeviceController.class);

	private static DataStoreConnector iotDataStore = null;
	private static ControlQueueConnector iotControlQueue = null;

	static {

		String trustStoreFile = null;
		String trustStorePassword = null;
		File certificateFile = null;

		try {
			trustStoreFile = DefaultDeviceControlConfigs.getInstance().getTrustStoreFile();
			trustStorePassword = DefaultDeviceControlConfigs.getInstance().getTrustStorePassword();
			certificateFile = new ResourceFileLoader("/resources/security/" + trustStoreFile)
					.getFile();

			if (certificateFile.exists()) {
				trustStoreFile = certificateFile.getAbsolutePath();
				log.info("Trust Store Path : " + trustStoreFile);

				System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
				System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
			} else {
				log.error("Trust Store not found in path : " + trustStoreFile);
			}
		} catch (ConfigurationException e1) {
			log.error("Error occured when trying to retreive Trust-Store-Certificate from path: "
							  + certificateFile, e1);
		}

		try {
			iotDataStore = IoTConfiguration.getInstance().getDataStore();
			iotControlQueue = IoTConfiguration.getInstance().getControlQueue();

			iotDataStore.initDataStore();
			iotControlQueue.initControlQueue();

		} catch (ConfigurationException ex) {
			log.error("Error creating DataStore or ControlQueue objects");
		} catch (InstantiationException ex) {
			log.error("Error creating DataStore or ControlQueue objects");
		} catch (IllegalAccessException ex) {
			log.error("Error creating DataStore or ControlQueue objects");
		}

	}

	@Path("/pushdata/{owner}/{type}/{id}/{time}/{key}/{value}")
	@POST
	// @Produces("application/xml")
	public static String pushData(@PathParam("owner") String owner,
								  @PathParam("type") String deviceType,
								  @PathParam("id") String deviceId, @PathParam("time") Long time,
								  @PathParam("key") String key, @PathParam("value") String value,
								  @HeaderParam("description") String description,
								  @Context HttpServletResponse response) {

		HashMap<String, String> deviceDataMap = new HashMap<String, String>();

		deviceDataMap.put("owner", owner);
		deviceDataMap.put("deviceType", deviceType);
		deviceDataMap.put("deviceId", deviceId);
		deviceDataMap.put("time", "" + time);
		deviceDataMap.put("key", key);
		deviceDataMap.put("value", value);
		deviceDataMap.put("description", description);

		//DeviceValidator deviceChecker = new DeviceValidator();

		//DeviceIdentifier dId = new DeviceIdentifier();
		//dId.setId(deviceId);
		//dId.setType(deviceType);

		//		try {
		//			boolean exists = deviceChecker.isExist(owner, dId);
		String result = "Failed to push";
		//			if (exists) {
		result = iotDataStore.publishIoTData(deviceDataMap);
		//
		//			}
		//
		return result;
		//
		//		} catch (InstantiationException e) {
		//			response.setStatus(500);
		//			return null;
		//		} catch (IllegalAccessException e) {
		//			response.setStatus(500);
		//			return null;
		//		} catch (ConfigurationException e) {
		//			response.setStatus(500);
		//			return null;
		//		} catch (DeviceCloudException e) {
		//			response.setStatus(500);
		//			return null;
		//		}

	}

	@Path("/setcontrol/{owner}/{type}/{id}/{key}/{value}")
	@POST
	public static String setControl(@PathParam("owner") String owner,
									@PathParam("type") String deviceType,
									@PathParam("id") String deviceId, @PathParam("key") String key,
									@PathParam("value") String value) {
		HashMap<String, String> deviceControlsMap = new HashMap<String, String>();

		deviceControlsMap.put("owner", owner);
		deviceControlsMap.put("deviceType", deviceType);
		deviceControlsMap.put("deviceId", deviceId);
		deviceControlsMap.put("key", key);
		deviceControlsMap.put("value", value);

		String result = null;
		result = iotControlQueue.enqueueControls(deviceControlsMap);
		return result;
	}

	// public static void main(String[] args) {
	//
	// DeviceController myController = new DeviceController();
	// String pushOut =
	// myController.pushData("10.100.7.38", "Arduino", "Shabirmean", "123456",
	// Long.parseLong("234890"), "Sensor", "23", "Testing");
	//
	// String setOut = myController.setControl("Shabirmean", "Arduino",
	// "123456", "13", "HIGH");
	//
	// System.out.println("---------------------------------------");
	// System.out.println("PUSH : " + pushOut);
	// System.out.println("---------------------------------------");
	// System.out.println("SET : " + setOut);
	// }
}
