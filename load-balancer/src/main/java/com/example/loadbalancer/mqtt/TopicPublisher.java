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
     * Publish delete commands to the relevant FS containers.
     *
     * Each chunk in the array carries the fsId that holds it (populated by
     * RoutingService when the file was uploaded). We send a targeted delete
     * command to each fsId's topic rather than broadcasting to all containers.
     *
     * Topic: fs/delete/{fsId}   — fsId matches FS_CONTAINER_ID env var (e.g. "fs-1")
     */
    public void publishDeleteCommands(long fileId, JsonArray chunks) throws MqttException {
        if (chunks == null || chunks.size() == 0) {
            // Fallback: broadcast to all four containers if no chunk metadata available
            System.err.println("[TopicPublisher] No chunk metadata for fileId=" + fileId +
                               ". Broadcasting delete to all FS containers.");
            String[] allFsIds = {"fs-1", "fs-2", "fs-3", "fs-4"};
            for (String fsId : allFsIds) {
                sendDeleteCommand(fileId, fsId, 0);
            }
            return;
        }

        for (int i = 0; i < chunks.size(); i++) {
            JsonObject chunk = chunks.get(i).getAsJsonObject();
            String fsId = chunk.has("fsId") ? chunk.get("fsId").getAsString() : "fs-" + (i + 1);
            int chunkOrder = chunk.has("chunkOrder") ? chunk.get("chunkOrder").getAsInt() : (i + 1);
            sendDeleteCommand(fileId, fsId, chunkOrder);
        }
    }

    private void sendDeleteCommand(long fileId, String fsId, int chunkOrder) throws MqttException {
        JsonObject deleteCmd = new JsonObject();
        deleteCmd.addProperty("fileId", fileId);
        deleteCmd.addProperty("fsId", fsId);
        deleteCmd.addProperty("chunkOrder", chunkOrder);

        String topic = "fs/delete/" + fsId;
        mqttBroker.publish(topic, deleteCmd.toString());
        System.out.println("[TopicPublisher] Sent delete command to " + topic +
                           " for fileId=" + fileId + ", chunkOrder=" + chunkOrder);
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
