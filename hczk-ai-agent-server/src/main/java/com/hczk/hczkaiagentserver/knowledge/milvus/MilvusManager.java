package com.hczk.hczkaiagentserver.knowledge.milvus;

import com.hczk.hczkaiagentserver.knowledge.config.EmbeddingProperties;
import com.hczk.hczkaiagentserver.knowledge.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.common.IndexParam;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusManager {

    private final MilvusProperties milvusProperties;
    private final EmbeddingProperties embeddingProperties;

    private volatile MilvusClientV2 client;
    private volatile boolean connected = false;
    private Timer reconnectTimer;
    private final Set<String> ensuredCollections = Collections.synchronizedSet(new HashSet<>());

    public void init() {
        connect();
        startReconnectTimer();
    }

    private void connect() {
        try {
            String address = milvusProperties.getAddress();
            if (!address.startsWith("http://") && !address.startsWith("https://")) {
                address = "http://" + address;
            }
            ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                    .uri(address);
            String token = buildToken();
            if (token != null) {
                builder.token(token);
            }
            client = new MilvusClientV2(builder.build());
            client.listCollections();
            connected = true;
            log.info("Milvus connected: {}", address);
        } catch (Exception e) {
            connected = false;
            log.error("Milvus connection failed: {}", e.getMessage());
        }
    }

    private String buildToken() {
        String username = milvusProperties.getUsername();
        String password = milvusProperties.getPassword();
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            return username + ":" + password;
        }
        return null;
    }

    private void startReconnectTimer() {
        reconnectTimer = new Timer("milvus-reconnect", true);
        reconnectTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!connected) {
                    log.info("Attempting Milvus reconnection...");
                    connect();
                }
            }
        }, 5000, 5000);
    }

    @PreDestroy
    public void destroy() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing Milvus client: {}", e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public MilvusClientV2 getClient() {
        if (!connected) {
            throw new RuntimeException("Milvus not connected");
        }
        return client;
    }

    public String buildCollectionName(String agentId, String collectionName) {
        return "kb_" + agentId + "_" + collectionName;
    }

    public void ensureCollection(String collectionName) {
        if (ensuredCollections.contains(collectionName)) {
            return;
        }
        try {
            boolean exists = getClient().hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName).build());
            if (!exists) {
                createCollection(collectionName);
            }
            loadCollectionIfNeeded(collectionName);
            ensuredCollections.add(collectionName);
        } catch (Exception e) {
            log.error("Failed to ensure collection {}: {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to ensure collection: " + e.getMessage());
        }
    }

    private void createCollection(String collectionName) {
        int dim = embeddingProperties.getDimension();
        var schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder().fieldName("id").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(64).isPrimaryKey(true).build());
        schema.addField(AddFieldReq.builder().fieldName("content").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(65535).build());
        schema.addField(AddFieldReq.builder().fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector)
                .dimension(dim).build());
        schema.addField(AddFieldReq.builder().fieldName("question").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(4096).build());
        schema.addField(AddFieldReq.builder().fieldName("question_vector").dataType(io.milvus.v2.common.DataType.FloatVector)
                .dimension(dim).build());
        schema.addField(AddFieldReq.builder().fieldName("source").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(512).build());
        schema.addField(AddFieldReq.builder().fieldName("title").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("chunk_index").dataType(io.milvus.v2.common.DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("content_hash").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(64).build());
        schema.addField(AddFieldReq.builder().fieldName("created_at").dataType(io.milvus.v2.common.DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("ingest_time").dataType(io.milvus.v2.common.DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("hit_count").dataType(io.milvus.v2.common.DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("adopt_count").dataType(io.milvus.v2.common.DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("relevance_score").dataType(io.milvus.v2.common.DataType.Float).build());
        schema.addField(AddFieldReq.builder().fieldName("chunk_type").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(16).build());

        // v11 新增：溯源信息字段
        schema.addField(AddFieldReq.builder().fieldName("page_number").dataType(io.milvus.v2.common.DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("chapter").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(512).build());
        schema.addField(AddFieldReq.builder().fieldName("context_pages").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("table_html").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(65535).build());
        schema.addField(AddFieldReq.builder().fieldName("source_filename").dataType(io.milvus.v2.common.DataType.VarChar)
                .maxLength(512).build());

        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder().fieldName("vector")
                .indexType(IndexParam.IndexType.IVF_FLAT).metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("nlist", 1024)).build());
        indexParams.add(IndexParam.builder().fieldName("question_vector")
                .indexType(IndexParam.IndexType.IVF_FLAT).metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("nlist", 1024)).build());

        getClient().createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName).collectionSchema(schema).indexParams(indexParams).build());
        log.info("Created Milvus collection: {}", collectionName);
    }

    private void loadCollectionIfNeeded(String collectionName) {
        try {
            Boolean loaded = getClient().getLoadState(GetLoadStateReq.builder()
                    .collectionName(collectionName).build());
            if (loaded == null || !loaded) {
                getClient().loadCollection(LoadCollectionReq.builder()
                        .collectionName(collectionName).build());
                log.info("Loaded collection into memory: {}", collectionName);
            }
        } catch (Exception e) {
            log.warn("Failed to check/load collection {}: {}", collectionName, e.getMessage());
        }
    }

    public List<String> listCollections(String agentId) {
        ListCollectionsResp resp = getClient().listCollections();
        List<String> all = resp.getCollectionNames();
        if (agentId == null || agentId.isEmpty()) {
            return all.stream().filter(n -> n.startsWith("kb_")).toList();
        }
        String prefix = "kb_" + agentId + "_";
        return all.stream().filter(n -> n.startsWith(prefix)).toList();
    }

    public void dropCollection(String collectionName) {
        getClient().dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
        ensuredCollections.remove(collectionName);
        log.info("Dropped collection: {}", collectionName);
    }

    public long getRowCount(String collectionName) {
        try {
            GetCollectionStatsResp resp = getClient().getCollectionStats(GetCollectionStatsReq.builder()
                    .collectionName(collectionName).build());
            return resp.getNumOfEntities() != null ? resp.getNumOfEntities() : 0L;
        } catch (Exception e) {
            return 0;
        }
    }

    public void flush(String collectionName) {
        getClient().flush(FlushReq.builder().collectionNames(List.of(collectionName)).build());
    }
}
