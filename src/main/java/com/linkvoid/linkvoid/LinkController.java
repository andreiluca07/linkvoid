package com.linkvoid.linkvoid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class LinkController {

    private final LinkRepository linkRepository;

    // Lee la variable de entorno "BASE_URL" del servidor. Si no existe (estás en local), usa localhost:8080 por defecto.
    private final String baseUrl = System.getenv("BASE_URL") != null 
        ? System.getenv("BASE_URL") 
        : "http://localhost:8080";

    // --- Control Anti-Spam por IP ---
    private final Map<String, List<Long>> requestCounts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 10; // Máximo de enlaces permitidos
    private static final long TIME_WINDOW = 60 * 1000; // En una ventana de 1 minuto

    public LinkController(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    // Método helper para validar el formato correcto de la URL
    private boolean isValidUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) 
                   && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    // 0. Servir la interfaz web con contador de generaciones y texto dinámico
    @GetMapping("/")
    public ResponseEntity<String> home() {
        String html = "<!DOCTYPE html>" +
                "<html lang='es'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<title>LinkVoid - Espacio Cósmico</title>" +
                "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap' rel='stylesheet'>" +
                "<style>" +
                "body { font-family: 'Inter', sans-serif; background: radial-gradient(circle at center, #0d0514 0%, #020003 100%); color: #e0d9f6; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; overflow: hidden; }" +
                ".card { background: rgba(8, 3, 13, 0.92); padding: 40px; border-radius: 16px; box-shadow: 0 0 35px rgba(138, 43, 226, 0.2); border: 1px solid rgba(147, 51, 234, 0.25); text-align: center; width: 420px; backdrop-filter: blur(12px); }" +
                "h2 { color: #d8b4fe; margin-top: 0; font-weight: 700; letter-spacing: 0.5px; text-shadow: 0 0 12px rgba(168, 85, 247, 0.6); }" +
                "p { color: #a78bfa; font-size: 14px; margin-bottom: 25px; }" +
                "input { width: 100%; padding: 12px 16px; font-size: 14px; margin-bottom: 20px; background: rgba(3, 1, 5, 0.85); border: 1px solid #6b21a8; border-radius: 8px; color: #fff; outline: none; box-sizing: border-box; transition: all 0.3s ease; }" +
                "input:focus { border-color: #c084fc; box-shadow: 0 0 14px rgba(192, 132, 252, 0.5); }" +
                "button { width: 100%; background: linear-gradient(135deg, #7e22ce, #581c87); color: white; border: none; padding: 12px 20px; font-size: 15px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: all 0.3s ease; box-shadow: 0 4px 15px rgba(126, 34, 206, 0.4); }" +
                "button:hover { background: linear-gradient(135deg, #9333ea, #6b21a8); box-shadow: 0 6px 22px rgba(147, 51, 234, 0.7); transform: translateY(-1px); }" +
                "#result { margin-top: 20px; font-size: 14px; overflow-wrap: break-all; color: #c4b5fd; }" +
                ".result-title { margin-bottom: 8px; font-weight: 600; color: #d8b4fe; font-size: 13px; }" +
                ".result-box { display: flex; align-items: center; justify-content: space-between; background: rgba(3, 1, 5, 0.85); border: 1px solid #6b21a8; border-radius: 8px; padding: 8px 12px; }" +
                ".result-box a { color: #f3e8ff; text-decoration: none; font-weight: 600; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 10px; }" +
                ".result-box a:hover { color: #d8b4fe; }" +
                ".copy-btn { background: linear-gradient(135deg, #7e22ce, #581c87); color: white; border: none; padding: 6px 14px; font-size: 12px; font-weight: 600; border-radius: 6px; cursor: pointer; transition: all 0.3s ease; white-space: nowrap; width: auto; box-shadow: 0 2px 10px rgba(126, 34, 206, 0.3); }" +
                ".copy-btn:hover { background: linear-gradient(135deg, #9333ea, #6b21a8); box-shadow: 0 4px 15px rgba(147, 51, 234, 0.5); transform: translateY(-1px); }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='card'>" +
                "<h2>LinkVoid</h2>" +
                "<p>Propulsa y acorta tus enlaces al vacío</p>" +
                "<input type='text' id='urlInput' placeholder='Pega tu URL cósmica aquí...' onkeydown=\"if(event.key === 'Enter') shortenUrl()\">" +
                "<button onclick='shortenUrl()'>Acortar Enlace</button>" +
                "<div id='result'></div>" +
                "</div>" +
                "<script>" +
                "let shortenCount = 0;" +
                "async function shortenUrl() {" +
                "  let url = document.getElementById('urlInput').value.trim();" +
                "  const resultDiv = document.getElementById('result');" +
                "  if (!url) { alert('Por favor, introduce una URL.'); return; }" +
                "  if (!url.startsWith('http://') && !url.startsWith('https://')) {" +
                "    url = 'https://' + url;" +
                "  }" +
                "  try {" +
                "    const response = await fetch('/shorten', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: url });" +
                "    const responseText = await response.text();" +
                "    if (!response.ok) {" +
                "      resultDiv.innerHTML = '<span style=\"color:#f87171;\">' + responseText + '</span>';" +
                "      return;" +
                "    }" +
                "    const data = JSON.parse(responseText);" +
                "    shortenCount++;" +
                "    const titleText = data.existed ? '¡Este enlace ya estaba acortado en el vacío!' : (shortenCount === 1 ? '¡Enlace propagado!' : '¡Enlace propagado de nuevo!');" +
                "    resultDiv.innerHTML = '<div class=\"result-title\">' + titleText + '</div><div class=\"result-box\"><a href=\"' + data.shortUrl + '\" target=\"_blank\">' + data.shortUrl + '</a><button class=\"copy-btn\" id=\"copyBtn\" onclick=\"copyToClipboard(\\\'' + data.shortUrl + '\\\')\">Copiar</button></div>';" +
                "  } catch (e) {" +
                "    resultDiv.innerHTML = '<span style=\"color:#f87171;\">Error al procesar la señal en el vacío.</span>';" +
                "  }" +
                "}" +
                "function copyToClipboard(text) {" +
                "  navigator.clipboard.writeText(text).then(() => {" +
                "    const btn = document.getElementById('copyBtn');" +
                "    btn.innerText = '¡Copiado!';" +
                "    btn.style.background = '#22c55e';" +
                "    btn.style.boxShadow = '0 0 10px rgba(34, 197, 94, 0.5)';" +
                "    setTimeout(() => {" +
                "      btn.innerText = 'Copiar';" +
                "      btn.style.background = 'linear-gradient(135deg, #7e22ce, #581c87)';" +
                "      btn.style.boxShadow = '0 2px 10px rgba(126, 34, 206, 0.3)';" +
                "    }, 2000);" +
                "  }).catch(err => {" +
                "    console.error('Error al copiar: ', err);" +
                "  });" +
                "}" +
                "</script>" +
                "</body>" +
                "</html>";

        return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(html);
    }

    // 1. Endpoint para acortar con control anti-spam, prevención de duplicados y concurrencia
    @PostMapping("/shorten")
    public ResponseEntity<Object> shortenUrl(@RequestBody String originalUrl, HttpServletRequest request) {
        String cleanedUrl = originalUrl.trim();

        // Validar si la URL introducida es una dirección válida
        if (!isValidUrl(cleanedUrl)) {
            return ResponseEntity.status(400)
                    .body("La señal introducida no es una URL cósmica válida.");
        }

        String clientIp = request.getRemoteAddr();
        long now = System.currentTimeMillis();

        requestCounts.putIfAbsent(clientIp, new ArrayList<>());
        List<Long> timestamps = requestCounts.get(clientIp);

        synchronized (timestamps) {
            // Limpiar peticiones antiguas fuera de la ventana de 1 minuto
            timestamps.removeIf(timestamp -> now - timestamp > TIME_WINDOW);

            if (timestamps.size() >= MAX_REQUESTS) {
                return ResponseEntity.status(429)
                        .body("Demasiadas señales enviadas al vacío. Espera un momento antes de acortar más enlaces.");
            }
            timestamps.add(now);
        }

        // 1. Comprobar si la URL ya existe en la base de datos
        Optional<Link> existingLink = linkRepository.findByOriginalUrl(cleanedUrl);
        if (existingLink.isPresent()) {
            return ResponseEntity.ok(Map.of(
                "shortUrl", baseUrl + "/" + existingLink.get().getShortCode(),
                "existed", true
            ));
        }

        // 2. Si no existe, intentar guardar manejando colisiones o accesos concurrentes
        String shortCode;
        Link link;

        while (true) {
            shortCode = UUID.randomUUID().toString().substring(0, 6);
            link = new Link(cleanedUrl, shortCode);
            try {
                linkRepository.save(link);
                break; 
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Si ocurre una colisión (por shortCode aleatorio o race condition de originalUrl),
                // comprobamos si otro hilo ya guardó la URL original justo ahora.
                Optional<Link> concurrentCheck = linkRepository.findByOriginalUrl(cleanedUrl);
                if (concurrentCheck.isPresent()) {
                    return ResponseEntity.ok(Map.of(
                        "shortUrl", baseUrl + "/" + concurrentCheck.get().getShortCode(),
                        "existed", true
                    ));
                }
                // Si fue colisión de shortCode aleatorio, el bucle repite
            }
        }

        return ResponseEntity.ok(Map.of(
            "shortUrl", baseUrl + "/" + shortCode,
            "existed", false
        ));
    }

    // 2. Endpoint de redirección con pantalla de transición espacial y cuenta atrás
    @GetMapping("/{shortCode}")
    public ResponseEntity<String> redirect(@PathVariable("shortCode") String shortCode) {
        Optional<Link> linkOpt = linkRepository.findByShortCode(shortCode);
        
        if (linkOpt.isPresent()) {
            Link link = linkOpt.get();
            
            // Incrementamos el contador y guardamos en la base de datos
            link.setClickCount(link.getClickCount() + 1);
            linkRepository.save(link);
            
            String targetUrl = link.getOriginalUrl();

            // Página HTML intermedia con estética espacial y cuenta atrás de 5 segundos
            String htmlTransition = "<!DOCTYPE html>" +
                    "<html lang='es'>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<title>LinkVoid - Propulsando...</title>" +
                    "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap' rel='stylesheet'>" +
                    "<style>" +
                    "body { font-family: 'Inter', sans-serif; background: radial-gradient(circle at center, #0d0514 0%, #020003 100%); color: #e0d9f6; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; overflow: hidden; }" +
                    ".card { background: rgba(8, 3, 13, 0.92); padding: 40px; border-radius: 16px; box-shadow: 0 0 35px rgba(138, 43, 226, 0.2); border: 1px solid rgba(147, 51, 234, 0.25); text-align: center; width: 420px; backdrop-filter: blur(12px); }" +
                    "h2 { color: #d8b4fe; margin-top: 0; font-weight: 700; letter-spacing: 0.5px; text-shadow: 0 0 12px rgba(168, 85, 247, 0.6); }" +
                    "p { color: #a78bfa; font-size: 15px; margin-bottom: 25px; }" +
                    ".countdown { font-size: 32px; font-weight: 700; color: #c084fc; text-shadow: 0 0 15px rgba(192, 132, 252, 0.6); margin-top: 15px; }" +
                    "</style>" +
                    "<script>" +
                    "let seconds = 5;" +
                    "window.onload = function() {" +
                    "  let timer = setInterval(function() {" +
                    "    seconds--;" +
                    "    document.getElementById('count').innerText = seconds;" +
                    "    if (seconds <= 0) {" +
                    "      clearInterval(timer);" +
                    "      window.location.href = '" + targetUrl + "';" +
                    "    }" +
                    "  }, 1000);" +
                    "}" +
                    "</script>" +
                    "</head>" +
                    "<body>" +
                    "<div class='card'>" +
                    "<h2>Propulsando al vacío...</h2>" +
                    "<p>Tu señal está siendo redirigida a través de LinkVoid.</p>" +
                    "<div class='countdown'><span id='count'>5</span>s</div>" +
                    "</div>" +
                    "</body>" +
                    "</html>";

            return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(htmlTransition);
        } else {
            String html404 = "<!DOCTYPE html>" +
                    "<html lang='es'>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<title>404 - LinkVoid</title>" +
                    "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap' rel='stylesheet'>" +
                    "<style>" +
                    "body { font-family: 'Inter', sans-serif; background: radial-gradient(circle at center, #0d0514 0%, #020003 100%); color: #e0d9f6; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; overflow: hidden; }" +
                    ".card { background: rgba(8, 3, 13, 0.92); padding: 40px; border-radius: 16px; box-shadow: 0 0 35px rgba(138, 43, 226, 0.2); border: 1px solid rgba(147, 51, 234, 0.25); text-align: center; width: 420px; backdrop-filter: blur(12px); }" +
                    "h2 { color: #f87171; margin-top: 0; font-weight: 700; text-shadow: 0 0 12px rgba(248, 113, 113, 0.6); }" +
                    "p { color: #a78bfa; font-size: 14px; }" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<div class='card'>" +
                    "<h2>404</h2>" +
                    "<p>La señal se ha perdido en el vacío cósmico.</p>" +
                    "</div>" +
                    "</body>" +
                    "</html>";

            return ResponseEntity.status(404).header("Content-Type", "text/html; charset=UTF-8").body(html404);
        }
    }
}