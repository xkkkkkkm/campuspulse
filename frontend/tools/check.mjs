import fs from 'node:fs';
import path from 'node:path';
import { parse } from 'acorn';
import { pathToFileURL } from 'node:url';
const root = path.resolve(import.meta.dirname, '..');
const javascript = fs.readdirSync(path.join(root, 'assets/js'), { recursive: true }).filter(file => file.endsWith('.js'));
for (const file of javascript) {
  const fullPath = path.join(root, 'assets/js', file);
  parse(fs.readFileSync(fullPath, 'utf8'), { ecmaVersion: 'latest', sourceType: 'module' });
  if (file !== 'script.js' && file !== 'pages/cover-selection.js') await import(pathToFileURL(fullPath));
}
for (const file of fs.readdirSync(root).filter(file => file.endsWith('.html'))) {
  const source = fs.readFileSync(path.join(root, file), 'utf8');
  if (/<script(?![^>]*src=)/i.test(source) || /\son\w+=/i.test(source)) throw new Error(`Inline script found: ${file}`);
  if (/cdnjs\.cloudflare\.com/.test(source)) throw new Error(`CDN icon dependency: ${file}`);
  if (!/type="module" src="assets\/js\/script.js"/.test(source)) throw new Error(`Missing module entry: ${file}`);
}
console.log(`Checked ${javascript.length} JavaScript modules and every HTML entry.`);
