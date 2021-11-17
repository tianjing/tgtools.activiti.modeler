package tgtools.activiti.modeler.gateway;

import org.activiti.engine.impl.util.ReflectUtil;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author 田径
 * @Title
 * @Description
 * @date 12:33
 */
@RequestMapping("/activiti/resource")
public class ResourceController {
    protected MimetypesFileTypeMap mimetypesFileTypeMap = new MimetypesFileTypeMap();


    @RequestMapping(value = "/editor-app/**")
    public void get(HttpServletRequest pRequest, HttpServletResponse pResponse) {
        String url = pRequest.getRequestURI();
        String file = url.substring(url.indexOf("editor-app"));
        int end = file.indexOf("?");
        if (end >= 0) {
            file = file.substring(0, file.indexOf("?"));
        }
        file = "tgtools/activiti/resource/" + file;
        try {
            pResponse.setContentType(getContentType(file));
            copyAndClose(ReflectUtil.getResourceAsStream(file), pResponse.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected String getContentType(String pFile) {
        if (pFile.endsWith(".xml")) {
            return "application/xml";
        }
        return mimetypesFileTypeMap.getContentType(pFile);
    }

    @RequestMapping(value = "/modeler.html")
    public void getmodeler(HttpServletRequest pRequest, HttpServletResponse pResponse) {
        try {
            pResponse.setContentType(mimetypesFileTypeMap.getContentType("modeler.html"));
            copyAndClose(ReflectUtil.getResourceAsStream("tgtools/activiti/resource/modeler.html"), pResponse.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyAndClose(InputStream pInputStream, OutputStream pOutputStream) {
        if (null == pOutputStream) {
            closeStream(pInputStream);
            return;
        }

        if (null == pInputStream && null != pOutputStream) {
            closeStream(pOutputStream);
            return;
        }

        try {
            int len = 0;
            byte[] data = new byte[1024];
            while ((len = pInputStream.read(data)) > 0) {
                pOutputStream.write(data, 0, len);
            }

        } catch (Exception e) {

        } finally {
            closeStream(pInputStream);
            closeStream(pOutputStream);
        }
    }

    private void closeStream(Closeable pCloseable) {
        if (null != pCloseable) {
            try {
                pCloseable.close();
            } catch (IOException e) {
            }
        }
    }
}
