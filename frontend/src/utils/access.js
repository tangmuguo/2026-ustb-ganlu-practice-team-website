import { userinfoStore } from "@/stores/userStore";
import { ElMessage } from 'element-plus'
import { useRouter } from "vue-router";


export function access(allowedLevels){
    const userinfo=userinfoStore()
    const userRouter=useRouter()
    const levels = Array.isArray(allowedLevels) ? allowedLevels : [allowedLevels]
    if(!userinfo.token || !userinfo.currentUser){
        ElMessage.info("请先登录")
        userRouter.replace('/login')
        return false
    }
    if(!levels.includes(userinfo.currentUser.level)){
        ElMessage.info("无访问权限")
        userRouter.replace('/')
        return false
    }
    return true
}
