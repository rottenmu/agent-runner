package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * API 接口定义服务。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public interface AiApiDocService extends IService<AiApiDoc> {

    /**
     * 解析 YApi 导出的 JSON 并批量导入接口定义。
     *
     * <p>支持两种格式：</p>
     * <ul>
     *   <li>YApi 接口列表数组：[{method,title,path,catname,req_query,req_body_other,res_body}]</li>
     *   <li>OpenAPI 3.0 文档：{openapi, paths:{...}}</li>
     * </ul>
     *
     * @param json YApi/OpenAPI JSON 文本
     * @return 成功导入的接口列表
     */
    List<AiApiDoc> importYApi(String json);

    /** 从 cURL bash 命令导入接口定义（支持多条）。 */
    List<AiApiDoc> importCurl(String curlText, String groupName);
}
