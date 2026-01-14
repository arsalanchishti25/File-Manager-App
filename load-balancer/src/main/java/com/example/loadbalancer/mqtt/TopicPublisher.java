package com.example.loadbalancer.mqtt;

import com.example.loadbalancer.model.AggregatorInfo;
import com.example.loadbalancer.model.FSContainerInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.util.List;

/**
 * Publishes MQTT messages to various topics.
 */
public class TopicPublisher {

    private final MqttBroker mqttBroker;

    public TopicPublisher(MqttBroker mqttBroker) {
        this.mqttBroker = mqttBroker;
    }

    /**
     * Publish upload routing response to Main App.
     * Topic: operations/response/{mainAppId}
     */
    public void publishUploadRoutingResponse(String mainAppId, long fileId, 
                                            AggregatorInfo aggregator, 
                                            List<FSContainerInfo> fsContainers) throws MqttException {
        JsonObject response = new JsonObject();
        response.addProperty("operation", "UPLOAD");
        response.addProperty("fileId", fileId);
        response.addProperty("success", true);

        // Add aggregator info
        JsonObject aggJson = new JsonObject();
        aggJson.addProperty("id", aggregator.getId());
        aggJson.addProperty("ip", aggregator.getIp());
        aggJson.addProperty("port", aggregator.getPort());
        response.add("aggregator", aggJson);

        // Add FS containers info
        JsonArray fsArray = new JsonArray();
        for (FSContainerInfo fs : fsContainers) {
            JsonObject fsJson = new JsonObject();
            fsJson.addProperty("id", fs.getId());
            fsJson.addProperty("ip", fs.getIp());
            fsJson.addProperty("port", fs.getPort());
            fsJson.addProperty("volumeGroup", fs.getVolumeGroup());
            fsArray.add(fsJson);
        }
        response.add("fsContainers", fsArray);

        String topic = "operations/response/" + mainAppId;
        mqttBroker.publish(topic, response.toString());
    }

    /**
     * Publish download routing response to Main App.
     * Topic: operations/response/{mainAppId}
     */
    public void publishDownloadRoutingResponse(String mainAppId, long fileId,
                                              AggregatorInfo aggregator,
                                              List<FSContainerInfo> fsContainers) throws MqttException {
        JsonObject response = new JsonObject();
        response.addProperty("operation", "DOWNLOAD");
        response.addProperty("fileId", fileId);
        response.addProperty("success", true);

        // Add aggregator info
        JsonObject aggJson = new JsonObject();
        aggJson.addProperty("id", aggregator.getId());
        aggJson.addProperty("ip", aggregator.getIp());
        aggJson.addProperty("port", aggregator.getPort());
        response.add("aggregator", aggJson);

        // Add FS containers info
        JsonArray fsArray = new JsonArray();
        for (FSContainerInfo fs : fsContainers) {
            JsonObject fsJson = new JsonObject();
            fsJson.addProperty("id", fs.getId());
            fsJson.addProperty("ip", fs.getIp());
            fsJson.addProperty("port", fs.getPort());
            fsJson.addProperty("volumeGroup", fs.getVolumeGroup());
            fsArray.add(fsJson);
        }
        response.add("fsContainers", fsArray);

        String topic = "operations/response/" + mainAppId;
        mqttBroker.publish(topic, response.toString());
    }

    /**
     * Publish delete commands to FS containers.
     * Topic: fs/delete/{fsId}
     */
    public void publishDeleteCommands(long fileId, JsonArray chunks) throws MqttException {
        // For now, send to hardcoded FS containers
        String[] fsIds = {"fs-1", "fs-2", "fs-3", "fs-4"};

        for (String fsId : fsIds) {
            JsonObject deleteCmd = new JsonObject();
            deleteCmd.addProperty("fileId", fileId);
            deleteCmd.addProperty("fsId", fsId);
            // TODO: Add chunk order based on volume group
            deleteCmd.addProperty("chunkOrder", 0);

            String topic = "fs/delete/" + fsId;
            mqttBroker.publish(topic, deleteCmd.toString());

            System.out.println("[TopicPublisher] Sent delete command to " + fsId);
        }
    }

    /**
     * Publish delete response to Main App.
     * Topic: operations/response/{mainAppId}
     */
    public void publishDeleteResponse(String mainAppId, long fileId, 
                                     boolean success, String message) throws MqttException {
        JsonObject response = new JsonObject();
        response.addProperty("operation", "DELETE");
        response.addProperty("fileId", fileId);
        response.addProperty("success", success);
        response.addProperty("message", message);

        String topic = "operations/response/" + mainAppId;
        mqttBroker.publish(topic, response.toString());
    }
}
