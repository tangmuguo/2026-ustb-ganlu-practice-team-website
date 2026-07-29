<script setup>

</script>

<template>
  <footer class="bg-dark text-white py-12 mt-20">
        <div class="container mx-auto px-4">
            <!-- 三列布局（内容空出，等待后端填充） -->
            <div class="grid grid-cols-1 md:grid-cols-3 gap-8 mb-8">
                <!-- 第一列：关于甘露 -->
                <div>
                    <h3 class="text-xl font-bold mb-4">关于甘露</h3>
                    <ul class="space-y-3">
                        <li><a href="#" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="about-ganlu">关于甘露</span>
                        </a></li>
                        <li><a href="#" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="contact-us">联系我们</span>
                        </a></li>
                        <li><a href="#" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="join-ganlu">加入甘露</span>
                        </a></li>
                    </ul>
                </div>
                
                <!-- 第二列：快速链接 -->
                <div>
                    <h3 class="text-xl font-bold mb-4">快速链接</h3>
                    <ul class="space-y-3">
                        <li><a href="/" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="quick-link1">首页</span>
                        </a></li>
                        <li><a href="/showm" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="quick-link2">课件</span>
                        </a></li>
                        <li><a href="/fengcai" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="quick-link3">风采</span>
                        </a></li>
                        <li><a href="/messageboard" class="text-gray-400 hover:text-white transition-colors flex items-center">
                            <i class="fa fa-angle-right mr-2"></i><span id="quick-link4">互动</span>
                        </a></li>
                    </ul>
                </div>
                
                <!-- 第三列：联系方式 -->
                <div>
                    <h3 class="text-xl font-bold mb-4">联系方式</h3>
                    <ul class="space-y-3">
                        <li class="flex items-start">
                            <i class="fa fa-map-marker text-gray-400 mt-1 mr-3"></i>
                            <span id="contact-address" class="text-gray-400">北京市海淀区学院路30号北京科技大学</span>
                        </li>
                        <li class="flex items-center">
                            <i class="fa fa-phone text-gray-400 mr-3"></i>
                            <span id="contact-phone" class="text-gray-400">16601298002</span>
                        </li>
                        <li class="flex items-center">
                            <i class="fa fa-envelope text-gray-400 mr-3"></i>
                            <span id="contact-email" class="text-gray-400">AdlineWu@163.com</span>
                        </li>
                    </ul>
                </div>
            </div>
            
            <!-- 版权所有和备案号 -->
            <div class="border-t border-gray-700 pt-8 text-center text-gray-500">
                <p>版权所有</p>
                <p>公安网站备案号</p>
            </div>
        </div>
    </footer>
</template>

<style scoped>

</style>