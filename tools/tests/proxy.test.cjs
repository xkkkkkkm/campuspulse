const {test,after,before}=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const {createServer}=require('../local_http_proxy.cjs');
let server,port;
before(async()=>{server=createServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));port=server.address().port;});
after(async()=>{server.closeAllConnections();await new Promise(r=>server.close(r));});
function request(path,method='GET'){return new Promise((resolve,reject)=>{http.request({host:'127.0.0.1',port,path,method},res=>{let body='';res.on('data',b=>body+=b);res.on('end',()=>resolve({status:res.statusCode,headers:res.headers,body}));}).on('error',reject).end();});}
test('malformed paths fail safely and subsequent requests work',async()=>{
  assert.equal((await request('/%ZZ')).status,400);
  assert.equal((await request('/%E0%A4%A')).status,400);
  assert.equal((await request('/')).status,200);
});
test('private, traversal and missing files are unavailable',async()=>{
  for(const p of ['/.env','/../.env','/%2e%2e%2f.env','/docs/axure-rebuild-plan.md','/assets/missing.js']) assert.equal((await request(p)).status,404);
});
test('HEAD works and security headers accompany HTML',async()=>{
  const res=await request('/','HEAD'); assert.equal(res.status,200); assert.equal(res.body,'');
  assert.match(res.headers['content-security-policy'],/script-src 'self'/);
  assert.equal((await request('/','POST')).status,405);
});
