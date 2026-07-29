/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}", // 包含 Vue 文件
  ],
  theme: {
    extend: {
      colors: {
        primary: '#1E88E5',
        secondary: '#4CAF50',
        accent: '#FFC107',
        dark: '#212121',
        light: '#F5F5F5'
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}

