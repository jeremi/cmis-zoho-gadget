import javax.ws.rs.Path
import javax.ws.rs.POST
import javax.ws.rs.FormParam
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import org.apache.commons.httpclient.methods.multipart.Part
import org.apache.commons.httpclient.methods.multipart.FilePart
import org.apache.commons.httpclient.methods.multipart.StringPart
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource
import javax.ws.rs.Consumes
import org.apache.commons.fileupload.FileItem

import org.apache.commons.httpclient.methods.PutMethod


@Path("zoho")
public class ZohoEndpoint {
  //you can get one at http://apihelp.wiki.zoho.com/Generate-API-Key.html
  def apiKey = "YOUR_ZOHO_API_KEY"
  //Change this to your server public URL
  def callbackURL = "http://www.xcmis.org/rest/zoho/save"


  @POST
  @Path("edit")
  public String edit(@FormParam("url") String url,
                     @FormParam("filename") String filename,
                     @FormParam("login") String login,
                     @FormParam("password") String password) {


    def client = new HttpClient()
    def ext = getExtension(filename)
    def postMethod = new PostMethod(getZohoURL(ext))

    def src = new ByteArrayPartSource(filename, get(url, login, password));
    Part[] parts = [
            new FilePart("content", src), //content of the file we want to open
            new StringPart("filename", filename), //    filename of the document with extension
            new StringPart("saveurl", callbackURL), //The Web URL should be a service that fetches the content of the updated document and saves it to the user specified location.
            new StringPart("id", getId(url, login, password)), //unique id that will be submitted while saving the document (for reference)
            new StringPart("format", ext) // the format in which document should be saved on remote server
    ];

    postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));

    try {
      def statusCode = client.executeMethod(postMethod)

      def resp = parseResponse(postMethod.getResponseBodyAsString())

      //if the status is not 200 (OK) or there was a problem in the opening of the file we throw an exception
      if (statusCode != 200 || resp["RESULT"] == "FALSE") {
        throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(resp["WARNING"]).type("text/plain").build())
      }
      return resp["URL"]
    } finally {
      postMethod.releaseConnection()
    }
  }

  @POST
  @Path("save")
  @Consumes(["multipart/*"])
  public String save(Iterator<FileItem> items) {
    def stream
    def id

    // We loop over all the POST parameters to find the one that are interesting for us.
    while (items.hasNext()) {
      FileItem item = items.next();

      if (item.getFieldName().equalsIgnoreCase("content")) {
        stream = item.getInputStream()
      }
      if (item.getFieldName().equalsIgnoreCase("id")) {
        id = new String(item.get());
      }
    }

    // We are preparing the request to send to the CMIS server
    // This time, we are going to send a PUT to update the content stream
    def client = new HttpClient()
    def putMethod = new PutMethod(getUrl(id))
    try {
      putMethod.setRequestBody(stream)
      putMethod.setRequestHeader("Authorization", "Basic " + getCredential(id))
      putMethod.setRequestHeader("Content-Type", "application/octet-stream")

      def statusCode = client.executeMethod(putMethod)

      // if everything went fine, we return a message that will be shown to the user
      if (statusCode >= 200 && statusCode < 300)
        return "Saved"
      // If it did not go well, we send an error, and the user will be notified that something went wrong while trying
      // to save his document
      throw new WebApplicationException(statusCode)
    } finally {
      putMethod.releaseConnection()
    }
  }

  /*
   * This is not very secure, it's just an easy way to have the url, login and pwd  when the Zoho server save
   * the document.
   * Instead, you might want store this information in the JCR and give the id of the node to Zoho.
   */
  private String getId(url, login, password) {
    return "$login:$password".getBytes().encodeBase64().toString() + ":" + url
  }

  private String getUrl(String id) {
    return id.substring(id.indexOf(":") + 1)
  }

  private String getCredential(String id) {
    return id.substring(0, id.indexOf(":"))
  }

  def parseResponse(respStr) {
    def resp = [:]
    respStr.eachLine {
      def pos = it.indexOf("=")
      if (pos > 0 && pos < it.length()) {
        resp[it.substring(0, pos)] = it.substring(pos + 1)
      }
    }
    return resp
  }

  /**
   * from the extension of the file, find which endpoint calling
   */
  private String getZohoURL(String extension) throws IOException {
    def url;
    if (extension == null) {
      throw new WebApplicationException(Response.Status.UNSUPPORTED_MEDIA_TYPE)
    }
    if (extension in ["doc", "rtf", "odt", "sxw", "html", "txt"])
      url = "http://export.writer.zoho.com/remotedoc.im"
    else if (extension in ["xls", "sxc", "csv"])
      url = "http://sheet.zoho.com/remotedoc.im"
    else if (extension in ["ppt", "pps"])
      url = "http://show.zoho.com/remotedoc.im"
    else
      throw new WebApplicationException(Response.Status.UNSUPPORTED_MEDIA_TYPE)
    url = url + "?apikey=" + apiKey + "&output=url"
    return url;
  }

  private String getExtension(String filename) {
    return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
  }

  public byte[] get(String url, String login, String password) {
    def encoded = "$login:$password".getBytes().encodeBase64().toString()
    def c = new URL(url).openConnection()
    c.setRequestProperty("Authorization", "Basic $encoded")

    def baos = new ByteArrayOutputStream()
    c.getInputStream().eachByte {
        baos.write([it] as byte[])
    }
    baos.flush()
    return baos.toByteArray()
  }
}
