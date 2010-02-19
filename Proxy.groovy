import javax.ws.rs.FormParam
import javax.ws.rs.Path
import javax.ws.rs.POST

@Path("proxy")
public class Proxy {
  @POST
  @Path("basic_auth")
  public String basic_auth(@FormParam("url") String url, @FormParam("login") String login,
                           @FormParam("password") String password) {
    return get(url, login, password);
  }

  public static String get(String url, String login, String password) {
    def encoded = "$login:$password".getBytes().encodeBase64().toString()
    def c = new URL(url).openConnection()
    c.setRequestProperty("Authorization", "Basic $encoded")
    return c.content.text
  }
}