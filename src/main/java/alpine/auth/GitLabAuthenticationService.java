package alpine.auth;

import static org.apache.http.impl.client.HttpClients.createDefault;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import javax.annotation.Nullable;
import javax.naming.AuthenticationException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.glassfish.jersey.server.ContainerRequest;

/**
 * @author Sven Lange
 * @since 1.5.x todo move to alpine
 */
public class GitLabAuthenticationService implements AuthenticationService {

  private final String gitlabUrl;
  private final String oAuthClientId;
  private final ContainerRequest request;

  public GitLabAuthenticationService(
      String gitlabUrl,
      String oAuthClientId,
      ContainerRequest request) {

    // todo hole gitlab auth token aus request

    this.gitlabUrl = gitlabUrl;
    this.oAuthClientId = oAuthClientId;
    this.request = request;
  }

  @Override
  public boolean isSpecified() {
    // existiert ein gitlab auth token? wenn ja return true und mache anschließend authenticate
    return false;
  }

  @Nullable
  @Override
  public Principal authenticate() throws AuthenticationException {

    // 1. request http://dieter/oauth/authorize?scope=openid&response_type=code&client_id=92837368bad2ff52f459e435a3e7e5b12506e3299d61111b70844139d8ccd8bc&state=qwertz&redirect_uri=http://localhost:8080/login/gitlab
    // 2. response http://localhost:8080/login/gitlab?code=8f7d41c4aa1c6122f2f982c9ff16af0a2efe9730db85596bcab92bf04caa4629&state=qwertz



    try (CloseableHttpClient httpclient = createDefault();) {
      URI uri = new URIBuilder()
          .setScheme("http")
          .setHost("dieter")
          .setPath("/")
          .setParameter("q", "httpclient")
          .setParameter("btnG", "Google Search")
          .setParameter("aq", "f")
          .setParameter("oq", "")
          .build();

      HttpGet httpget = new HttpGet(uri);
      CloseableHttpResponse response = httpclient.execute(httpget);
      System.out.println(response.getStatusLine());

    } catch (Exception e) {
      e.printStackTrace();
    }

    // ermittle principal und gebe in zurück

    return null;
  }
}
