package tgtools.activiti.modeler.gateway;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.util.ReflectUtil;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import tgtools.web.entity.GridData;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * activiti 模型设计器处理类
 */
@RequestMapping("/activiti")
@RestController
public class ModelController {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ModelController.class);
    String MODEL_ID = "modelId";
    String MODEL_NAME = "name";
    String MODEL_REVISION = "revision";
    String MODEL_DESCRIPTION = "description";
    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @RequestMapping(value = "/model", method = {RequestMethod.GET})
    ModelAndView model() {
        return new ModelAndView("act/model/model");
    }

    @RequestMapping(value = "/model/list", method = {RequestMethod.POST})
    public GridData list(@RequestParam("pageIndex") int pIndex, @RequestParam("pageSize") int pPageSize) {
        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
        long count = processEngine.getRepositoryService().createModelQuery().count();
        List<Model> models = processEngine.getRepositoryService().createModelQuery().orderByCreateTime().asc().listPage((pIndex*pPageSize), pPageSize);
        GridData entity = new GridData();
        entity.setTotalRows((int) count);
        entity.setCurPage(1);
        entity.setData(models);
        return entity;
    }

    @RequestMapping(value = "/model/add", method = {RequestMethod.GET})
    public ResponseCode newModel(HttpServletResponse response) throws UnsupportedEncodingException {

        //初始化一个空模型
        Model model = repositoryService.newModel();
        //设置一些默认信息
        String name = "new-process";
        String description = "";
        int revision = 1;
        String key = "process";

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(MODEL_NAME, name);
        modelNode.put(MODEL_DESCRIPTION, description);
        modelNode.put(MODEL_REVISION, revision);

        model.setName(name);
        model.setKey(key);
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String id = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id, editorNode.toString().getBytes("utf-8"));
        ResponseCode rc=ResponseCode.ok(id);
        rc.put("success",true);
        rc.put("data",id);
        return rc;
    }

    @RequestMapping(value = "/model/{modelId}/json", method = {RequestMethod.GET})
    public ObjectNode getEditorJson(@PathVariable String modelId) {
        ObjectNode modelNode = null;
        Model model = repositoryService.getModel(modelId);
        if (model != null) {
            try {
                if (StringUtils.isNotEmpty(model.getMetaInfo())) {
                    modelNode = (ObjectNode) objectMapper.readTree(model.getMetaInfo());
                } else {
                    modelNode = objectMapper.createObjectNode();
                    modelNode.put(MODEL_NAME, model.getName());
                }
                modelNode.put(MODEL_ID, model.getId());
                ObjectNode editorJsonNode = (ObjectNode) objectMapper.readTree(
                        new String(repositoryService.getModelEditorSource(model.getId()), "utf-8"));
                modelNode.put("model", editorJsonNode);

            } catch (Exception e) {
                LOGGER.error("Error creating model JSON", e);
                throw new ActivitiException("Error creating model JSON", e);
            }
        }
        return modelNode;
    }

    @RequestMapping(value = "/editor/stencilset", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    public String getStencilset() {
        InputStream stencilsetStream = ReflectUtil.getResourceAsStream("tgtools/activiti/stencilset.json");
        try {
            return IOUtils.toString(stencilsetStream, "utf-8");
        } catch (Exception e) {
            throw new ActivitiException("Error while loading stencil set", e);
        } finally {
            if (null != stencilsetStream) {
                try {
                    stencilsetStream.close();
                } catch (IOException e) {
                    LOGGER.error("stencilsetStream 释放出错；原因：" + e.getMessage(), e);
                }
            }
        }
    }

    @RequestMapping(value = "/model/edit/{id}", method = RequestMethod.GET)
    public void edit(HttpServletResponse response, @PathVariable("id") String id) {
        try {
            response.sendRedirect("/modeler.html?modelId=" + id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "/model/remove/{id}", method = RequestMethod.POST)
    public ResponseCode remove(@PathVariable("id") String id) {
        repositoryService.deleteModel(id);
        return ResponseCode.ok();
    }

    @RequestMapping(value = "/model/deploy/{id}", method = RequestMethod.POST)
    public ResponseCode deploy(@PathVariable("id") String id) throws Exception {

        //获取模型
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return ResponseCode.error("模型数据为空，请先设计流程并成功保存，再进行发布。");
        }

        JsonNode modelNode = new ObjectMapper().readTree(bytes);

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if (model.getProcesses().size() == 0) {
            return ResponseCode.error("数据模型不符要求，请至少设计一条主线流程。");
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);

        return ResponseCode.ok();
    }

    @RequestMapping(value = "/model/batchRemove", method = RequestMethod.POST)
    public ResponseCode batchRemove(@RequestParam("ids[]") String[] ids) {
        for (String id : ids) {
            repositoryService.deleteModel(id);
        }
        return ResponseCode.ok();
    }

    @RequestMapping(value = "/model/{modelId}/save", method = RequestMethod.PUT)
    @ResponseStatus(value = HttpStatus.OK)
    public void saveModel(@PathVariable String modelId
            , String name, String description
            , String json_xml, String svg_xml) {
        try {

            Model model = repositoryService.getModel(modelId);

            ObjectNode modelJson = (ObjectNode) objectMapper.readTree(model.getMetaInfo());

            modelJson.put(MODEL_NAME, name);
            modelJson.put(MODEL_DESCRIPTION, description);
            model.setMetaInfo(modelJson.toString());
            model.setName(name);

            repositoryService.saveModel(model);

            repositoryService.addModelEditorSource(model.getId(), json_xml.getBytes("utf-8"));

            InputStream svgStream = new ByteArrayInputStream(svg_xml.getBytes("utf-8"));
            TranscoderInput input = new TranscoderInput(svgStream);

            PNGTranscoder transcoder = new PNGTranscoder();
            // Setup output
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(outStream);

            // Do the transformation
            transcoder.transcode(input, output);
            final byte[] result = outStream.toByteArray();
            repositoryService.addModelEditorSourceExtra(model.getId(), result);
            outStream.close();

        } catch (Exception e) {
            LOGGER.error("Error saving model", e);
            throw new ActivitiException("Error saving model", e);
        }
    }

    @RequestMapping(value = "/model/export/{id}", method = RequestMethod.GET)
    public void exportToXml(@PathVariable("id") String id, HttpServletResponse response) {
        try {
            Model modelData = repositoryService.getModel(id);
            BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
            JsonNode editorNode = new ObjectMapper().readTree(repositoryService.getModelEditorSource(modelData.getId()));
            BpmnModel bpmnModel = jsonConverter.convertToBpmnModel(editorNode);
            BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
            byte[] bpmnBytes = xmlConverter.convertToXML(bpmnModel);

            ByteArrayInputStream in = new ByteArrayInputStream(bpmnBytes);
            IOUtils.copy(in, response.getOutputStream());
            String filename = bpmnModel.getMainProcess().getId() + ".bpmn20.xml";
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            response.flushBuffer();
        } catch (Exception e) {
            throw new ActivitiException("导出model的xml文件失败，模型ID=" + id, e);
        }
    }

    private static class PageUtils implements Serializable {
        private static final long serialVersionUID = 1L;
        private int total;
        private List<?> rows;

        public PageUtils(List<?> list, int total) {
            this.rows = list;
            this.total = total;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public List<?> getRows() {
            return rows;
        }

        public void setRows(List<?> rows) {
            this.rows = rows;
        }

    }

    private static class ResponseCode extends HashMap<String, Object> {

        private static final long serialVersionUID = 1L;

        public ResponseCode() {
            put("code", 0);
            put("msg", "操作成功");
        }

        public static ResponseCode error() {
            return error(1, "操作失败");
        }

        public static ResponseCode error(String msg) {
            return error(500, msg);
        }

        public static ResponseCode error(int code, String msg) {
            ResponseCode r = new ResponseCode();
            r.put("code", code);
            r.put("msg", msg);
            return r;
        }

        public static ResponseCode ok(String msg) {
            ResponseCode r = new ResponseCode();
            r.put("msg", msg);
            return r;
        }

        public static ResponseCode ok(Map<String, Object> map) {
            ResponseCode r = new ResponseCode();
            r.putAll(map);
            return r;
        }

        public static ResponseCode ok() {
            return new ResponseCode();
        }

        @Override
        public ResponseCode put(String key, Object value) {
            super.put(key, value);
            return this;
        }
    }

}
