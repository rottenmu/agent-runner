package com.zimo.module.sys;

import com.zimo.framework.common.PluginRegister;

public class SysPluginRegister implements PluginRegister {
    @Override
    public String getPluginId() { return "sys"; }
    @Override
    public String getPluginName() { return "系统管理"; }
    @Override
    public String getApiPrefix() { return "/api/biz/sys"; }
    @Override
    public String getFrontendRoute() { return "/biz/sys"; }
    @Override
    public String getFrontendModule() { return "sys"; }
    @Override
    public String getAgentName() { return "sys-agent"; }
    @Override
    public int getOrder() { return 6; }
}
