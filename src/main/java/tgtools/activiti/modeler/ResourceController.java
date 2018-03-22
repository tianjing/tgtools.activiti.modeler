package tgtools.activiti.modeler;

import org.activiti.engine.impl.util.ReflectUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RestController
public class ResourceController {

    @RequestMapping(value = "/editor-app")
    public void get(HttpServletRequest pRequest, HttpServletResponse pResponse) {
        String url =pRequest.getRequestURI();
        String file =url.substring(url.indexOf("editor-app"));
        file=file.substring(0,file.indexOf("?"));
        file ="tgtools/activiti/resource/"+file;
        System.out.println("file:"+file);
        try {
            copyAndClose(ReflectUtil.getResourceAsStream(file), pResponse.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "/modeler.html")
    public void getmodeler(HttpServletRequest pRequest, HttpServletResponse pResponse) {
        try {
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
