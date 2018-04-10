#tgtools.activiti.modeler

tgtools activiti modeler view project

一个独立的 activiti modeler 库 包含了模型页面和画图时用到的功能

spring boot 用法

1、在config 类中 加入 一下代码

   @Bean
    public ModelController modelController(ProcessEngine processEngine){
        return new ModelController();
    }
    
    @Bean
    public ResourceController resourceController(ProcessEngine processEngine){
        return new ResourceController();
    }
    
2、请求地址 activiti/resource/modeler.html?modelId=57501

如 localhost:8080/activiti/resource/modeler.html?modelId=57501


