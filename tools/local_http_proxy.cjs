'use strict';
const http = require('node:http');
const https = require('node:https');
const fs = require('node:fs');
const path = require('node:path');

const MIME = { '.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.mjs':'text/javascript; charset=utf-8', '.json':'application/json; charset=utf-8', '.svg':'image/svg+xml', '.png':'image/png', '.jpg':'image/jpeg', '.jpeg':'image/jpeg', '.gif':'image/gif', '.webp':'image/webp', '.ico':'image/x-icon', '.woff':'font/woff', '.woff2':'font/woff2' };
const SECURITY_HEADERS = {
  'X-Content-Type-Options':'nosniff', 'X-Frame-Options':'DENY', 'Referrer-Policy':'same-origin',
  'Permissions-Policy':'camera=(), microphone=(), geolocation=()',
  'Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data: blob:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"
};
function error(res, status, message) {
  if (res.destroyed) return;
  if (res.headersSent) { res.destroy(); return; }
  res.writeHead(status, {'Content-Type':'text/plain; charset=utf-8', ...SECURITY_HEADERS});
  res.end(message);
}
function within(root, candidate) {
  const rel=path.relative(root,candidate);
  return rel!== '..' && !rel.startsWith(`..${path.sep}`) && !path.isAbsolute(rel);
}
function createServer({root=path.resolve(__dirname,'..','frontend'), target=process.env.TARGET || 'http://127.0.0.1:8080'}={}) {
  const upstream=new URL(target);
  if (!['http:','https:'].includes(upstream.protocol)) throw new Error('TARGET must be an HTTP(S) URL');
  root=fs.realpathSync(root);
  return http.createServer(async (req,res)=>{
    for (const [k,v] of Object.entries(SECURITY_HEADERS)) res.setHeader(k,v);
    try {
      const url=new URL(req.url,'http://localhost');
      const decoded=decodeURIComponent(url.pathname);
      if (decoded.includes('\0') || decoded.includes('\\')) return error(res,400,'Invalid path');
      if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/uploads/')) {
        const headers={...req.headers,host:upstream.host};
        delete headers.connection; delete headers['proxy-connection'];
        const client=upstream.protocol==='https:' ? https : http;
        const proxy=client.request({protocol:upstream.protocol,hostname:upstream.hostname,port:upstream.port,method:req.method,path:url.pathname+url.search,headers},response=>{
          if (res.destroyed) { response.destroy(); return; }
          const outgoing={...response.headers,...SECURITY_HEADERS};
          delete outgoing.connection;
          res.writeHead(response.statusCode || 502,outgoing);
          response.on('error',()=>res.destroy());
          response.pipe(res);
        });
        proxy.setTimeout(60000,()=>proxy.destroy(new Error('upstream timeout')));
        proxy.on('error',()=>error(res,502,'Backend unavailable'));
        req.on('aborted',()=>proxy.destroy());
        res.on('close',()=>proxy.destroy());
        req.pipe(proxy);
        return;
      }
      if (!['GET','HEAD'].includes(req.method)) return error(res,405,'Method not allowed');
      if (url.pathname==='/healthz') {
        res.writeHead(200,{'Content-Type':'application/json'}); res.end(req.method==='HEAD' ? undefined : '{"status":"UP"}'); return;
      }
      const relative=decoded==='/' ? 'index.html' : decoded.slice(1);
      if (relative.split('/').some(p=>p.startsWith('.')) || !(/^[^/]+\.html$/.test(relative) || relative.startsWith('assets/'))) return error(res,404,'Not found');
      const candidate=path.resolve(root,relative);
      if (!within(root,candidate)) return error(res,404,'Not found');
      let real,stat;
      try { real=await fs.promises.realpath(candidate); stat=await fs.promises.stat(real); }
      catch { return error(res,404,'Not found'); }
      const contentType=MIME[path.extname(real).toLowerCase()];
      if (!within(root,real) || !stat.isFile() || !contentType) return error(res,404,'Not found');
      res.writeHead(200,{'Content-Type':contentType,'Content-Length':stat.size,'Cache-Control':relative.endsWith('.html') ? 'no-cache' : 'public, max-age=300'});
      if (req.method==='HEAD') { res.end(); return; }
      const stream=fs.createReadStream(real);
      stream.on('error',()=>error(res,500,'Unable to read resource'));
      res.on('close',()=>stream.destroy());
      stream.pipe(res);
    } catch (e) { error(res,e instanceof URIError || e instanceof TypeError ? 400 : 500,'Invalid request'); }
  });
}
if(require.main===module) {
  const port=Number(process.env.PORT || 8125), host=process.env.HOST || '127.0.0.1';
  const server=createServer();
  server.listen(port,host,()=>console.log(`CampusPulse frontend: http://${host}:${port}`));
  for (const signal of ['SIGTERM','SIGINT']) process.on(signal,()=>{
    server.close(()=>process.exit(0));
    setTimeout(()=>process.exit(0),5000).unref();
  });
}
module.exports={createServer,within};
