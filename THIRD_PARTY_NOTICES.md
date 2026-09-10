# Third-Party Notices

The [MIT License](LICENSE) covers the CampusPulse project code and original documentation to the extent of the contributors' rights. Third-party software, fonts, images and trademarks retain their own rights and licenses; the project license does not relicense them.

## Bundled Font Awesome Free

The frontend includes Font Awesome Free **6.7.2** from Fonticons, Inc., under `frontend/assets/vendor/fontawesome/`. The local files comprise CSS and WOFF2 fonts. The upstream copyright and license text is preserved at [frontend/assets/vendor/fontawesome/LICENSE.txt](frontend/assets/vendor/fontawesome/LICENSE.txt), and the CSS retains its attribution comment.

| Material | Applicable upstream license |
| --- | --- |
| Font Awesome non-font/non-icon code, including CSS | MIT License |
| Font Awesome webfonts | SIL Open Font License 1.1, with the reserved font name “Font Awesome” |
| Font Awesome icons distributed as SVG/JS, if added from upstream | CC BY 4.0; consult the upstream license for those formats |

Copyright © 2024 Fonticons, Inc. See [Font Awesome Free licensing](https://fontawesome.com/license/free) and the bundled license for the complete terms. The upstream license notice is reproduced without changing its scope. Font Awesome brand icons represent their respective trademark owners and imply no endorsement of CampusPulse.

## Dependencies installed during build or testing

Java dependencies are declared in [backend/pom.xml](backend/pom.xml); frontend development/test dependencies in [frontend/package.json](frontend/package.json) and its lockfile; model dependencies in [ml/requirements.txt](ml/requirements.txt); support-service dependencies in [support-agent/requirements.txt](support-agent/requirements.txt). MySQL, Java/Maven, Node and Python images are referenced by [Compose](docker-compose.yml) and the [backend](backend/Dockerfile), [frontend](frontend/Dockerfile) and [support-agent](support-agent/Dockerfile) Dockerfiles. These components keep their upstream licenses and bundled notices. Installing or building them does not make them MIT-licensed CampusPulse code.

The source repository does not vendor Maven caches, npm `node_modules`, Python virtual environments or base container images. When redistributing assembled JARs, container images or dependency packages, retain the notices that accompany those distributions and review their applicable terms. This file is an inventory of the deliberately bundled frontend asset, not a generated complete software bill of materials for all transitive dependencies.

## Course materials, data and institutional references

Historical course binaries and delivery archives are excluded from the public source release. Retained Markdown and diagram sources document the development process and may refer to earlier designs; they are not third-party endorsement or proof of current runtime behavior. University names, logos and other third-party trademarks are not relicensed by the project MIT license. The project presentation does not claim institutional endorsement.

Demo records are synthetic application fixtures and do not establish real adoption, measured recommendation performance or individual authorship. User-provided content, uploaded images and private deployment data are not part of the distributed source license. Local backups and model artifacts should remain outside the source release.

中文说明：项目 MIT 许可证不改变第三方字体、代码和商标的原有权利。Font Awesome 的完整许可证随本地资源保留；课程旧二进制、真实用户数据、上传、密钥和备份不进入源码发布。仓库不暗示学校背书，也不将合作项目全部归于某一位个人。
