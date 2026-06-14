package com.hczk.hczkaiagentserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
    
    @Select("SELECT * FROM api_keys WHERE api_key = #{apiKey} AND status = 'active' LIMIT 1")
    ApiKey findByApiKey(String apiKey);
}
