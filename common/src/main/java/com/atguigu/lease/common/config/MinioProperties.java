package com.atguigu.lease.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "minio")
@Data
@Component
public class MinioProperties {

    //@Value("${minio.endpoint}")
    private String endpoint;

    private String accesskey;

    private String secretkey;

    private String bucketname;
}
