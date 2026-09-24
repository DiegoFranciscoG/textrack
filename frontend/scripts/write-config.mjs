// Genera public/config.json en el build de Vercel a partir de TEXTRACK_API_URL (URL pública de la API en Render).
import { writeFileSync } from 'node:fs';

const apiUrl = (process.env.TEXTRACK_API_URL ?? '').trim().replace(/\/+$/, '');
if (apiUrl && !/^https:\/\/[\w.-]+(:\d+)?$/.test(apiUrl)) {
  console.error('TEXTRACK_API_URL debe ser https://host sin ruta');
  process.exit(1);
}
writeFileSync('public/config.json', JSON.stringify({ apiUrl }, null, 2) + '\n');
console.log(`config.json → apiUrl=${apiUrl || '(mismo origen)'}`);
