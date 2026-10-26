package com.cwnupaa.backend.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * 反序列化时先去掉字符串前后空格，再交给 Bean Validation 校验。
 *
 * <p>仅用于注册表单这类纯文本字段（通过 {@code @JsonDeserialize} 逐个标注），
 * 不做全局替换——公告正文、题面等允许保留缩进的内容不受影响。</p>
 */
public class TrimStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return TextUtils.trim(parser.getValueAsString());
    }
}
