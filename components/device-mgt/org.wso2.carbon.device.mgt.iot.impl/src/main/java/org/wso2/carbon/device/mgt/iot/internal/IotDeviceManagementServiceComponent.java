/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.device.mgt.iot.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.device.mgt.iot.arduino.firealarm.impl.FireAlarmManager;
import org.wso2.carbon.device.mgt.iot.common.IotDeviceMgtPluginException;
import org.wso2.carbon.device.mgt.iot.config.IotDeviceConfigurationManager;
import org.wso2.carbon.device.mgt.iot.config.IotDeviceManagementConfig;
import org.wso2.carbon.device.mgt.iot.config.datasource.IotDataSourceConfig;
import org.wso2.carbon.device.mgt.iot.dao.IotDeviceManagementDAOFactory;
import org.wso2.carbon.device.mgt.iot.dao.util.IotDeviceManagementDAOUtil;
import org.wso2.carbon.ndatasource.core.DataSourceService;
import org.wso2.carbon.device.mgt.common.spi.DeviceMgtService;

import javax.sql.DataSource;

import java.util.Map;

/**
 * @scr.component name="org.wso2.carbon.device.mgt.iot.impl.internal.IotDeviceManagementServiceComponent"
 * immediate="true"
 * @scr.reference name="org.wso2.carbon.ndatasource"
 * interface="org.wso2.carbon.ndatasource.core.DataSourceService"
 * cardinality="1..1"
 * policy="dynamic"
 * bind="setDataSourceService"
 * unbind="unsetDataSourceService"
 * <p/>
 * Adding reference to API Manager Configuration service is an unavoidable hack to get rid of NPEs thrown while
 * initializing APIMgtDAOs attempting to register APIs programmatically. APIMgtDAO needs to be proper cleaned up
 * to avoid as an ideal fix
 */
public class IotDeviceManagementServiceComponent {
	

    private ServiceRegistration serverStartupObserverRef;
    private ServiceRegistration arduinoServiceRegRef;
    //private ServiceRegistration raspberryServiceRegRef;


    private static final Log log = LogFactory.getLog(IotDeviceManagementServiceComponent.class);

    protected void activate(ComponentContext ctx) {
    	if (log.isDebugEnabled()) {
            log.debug("Activating Iot Device Management Service Component");
        }
        try {
            BundleContext bundleContext = ctx.getBundleContext();

            /* Initialize the data source configuration */
            IotDeviceConfigurationManager.getInstance().initConfig();
            IotDeviceManagementConfig config = IotDeviceConfigurationManager.getInstance()
                    .getIotDeviceManagementConfig();
            Map<String, IotDataSourceConfig> dsConfigMap =
                    config.getIotDeviceMgtRepository().getIotDataSourceConfigMap();

            IotDeviceManagementDAOFactory.init(dsConfigMap);

            String setupOption = System.getProperty("setup");
            if (setupOption != null) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "-Dsetup is enabled. Iot Device management repository schema initialization is about " +
                                    "to begin");
                }
                try {
                    for (String pluginType : dsConfigMap.keySet()){
                        IotDeviceManagementDAOUtil
                                .setupIotDeviceManagementSchema(IotDeviceManagementDAOFactory.getDataSourceMap
                                        ().get(pluginType));
                    }
                } catch (IotDeviceMgtPluginException e) {
                    log.error("Exception occurred while initializing mobile device management database schema", e);
                }
            }

            arduinoServiceRegRef =
                    bundleContext.registerService(DeviceMgtService.class.getName(), new
					FireAlarmManager(),
												  null);
           

            if (log.isDebugEnabled()) {
                log.debug("Iot Device Management Service Component has been successfully activated");
            }
        } catch (Throwable e) {
            log.error("Error occurred while activating Iot Device Management Service Component", e);
        }
    }

    protected void deactivate(ComponentContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("De-activating Iot Device Management Service Component");
        }
        try {
            if (arduinoServiceRegRef != null) {
                arduinoServiceRegRef.unregister();
            }
            
            if (log.isDebugEnabled()) {
                log.debug(
                        "IOT Device Management Service Component has been successfully de-activated");
            }
        } catch (Throwable e) {
            log.error("Error occurred while de-activating Iot Device Management bundle", e);
        }
    }

    protected void setDataSourceService(DataSourceService dataSourceService) {
        /* This is to avoid iot device management component getting initialized before the underlying datasources
        are registered */
        if (log.isDebugEnabled()) {
            log.debug("Data source service set to iot service component");
        }
    }

    protected void unsetDataSourceService(DataSourceService dataSourceService) {
        //do nothing
    }
    
    
}
