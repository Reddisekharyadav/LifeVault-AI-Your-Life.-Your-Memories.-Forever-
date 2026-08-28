# 🌐 LifeVault AI — Web Download Landing Page & Custom Domain Guide

This `/docs` folder is configured as a standalone, responsive static website allowing your friends and testers to download the **`lifevault-ai.apk`** directly from your website or custom domain.

---

## 📁 What is in this `/docs` folder?

- **`index.html`**: A web landing page with:
  - Direct download button for `lifevault-ai.apk`.
  - Step-by-step installation instructions for Android devices.
  - Feature highlights (100% Offline, GZIP Compression, Voice Studio, Family Tree, Master PIN locker).
  - Dark-mode responsive design optimized for mobile and desktop screens.
- **`lifevault-ai.apk`**: The pre-compiled Android APK ready for immediate download and installation.

---

## 🚀 How to Host on GitHub Pages with your Custom Domain

### Step 1: Push Repository to GitHub
1. In Google AI Studio, click the **Export** / **GitHub Sync** button to push this codebase to your GitHub repository.

### Step 2: Enable GitHub Pages from `/docs`
1. Open your repository on [GitHub](https://github.com).
2. Go to **Settings** $\rightarrow$ **Pages** (under the "Code and automation" section).
3. Under **Build and deployment** $\rightarrow$ **Branch**:
   - Select your branch (e.g. `main`).
   - Select the folder: **`/docs`**.
   - Click **Save**.

### Step 3: Connect Your Custom Domain (Optional)
1. In the same **Settings** $\rightarrow$ **Pages** menu, scroll down to **Custom domain**.
2. Enter your domain name (e.g., `vault.yourdomain.com` or `yourdomain.com`) and click **Save**.
3. In your DNS provider (Cloudflare, GoDaddy, Namecheap, Google Domains, etc.):
   - **For Subdomain (e.g., `vault.yourdomain.com`)**: Add a `CNAME` record pointing to `<your-github-username>.github.io`.
   - **For Apex Domain (e.g., `yourdomain.com`)**: Add `A` records pointing to GitHub Pages IP addresses:
     ```
     185.199.108.153
     185.199.109.153
     185.199.110.153
     185.199.111.153
     ```
4. Check **Enforce HTTPS** on GitHub Pages.

---

## 📱 How Your Friends Download the APK
Once deployed:
1. Share your website URL (e.g., `https://yourdomain.com` or `https://<username>.github.io/<repo-name>/`) with your friends.
2. Friends open the page on their Android device and tap **Download Android APK**.
3. Follow the simple on-screen instructions to install and test!
