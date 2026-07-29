import instance from "@/utils/http"
import AllPATH from "@/utils/path"

export function uploadImage (imageFile) {

  return instance({
    url: AllPATH.uploadImagePath,
    method: 'POST',
    data: {
      imageFile: imageFile
    },
    headers: {
      "Content-Type": "multipart/form-data", // 关键！设置正确的 Content-Type
    }
  })
}

export function uploadMaterial (materialFile) {

  return instance({
    url: AllPATH.uploadMaterialPath,
    method: 'POST',
    data: {
      materialFile: materialFile
    },
    headers: {
      "Content-Type": "multipart/form-data", // 关键！设置正确的 Content-Type
    }
  })
}

export function uploadWholeMaterial (val) {

  return instance({
    url: AllPATH.uploadWholeMaterialPath,
    method: 'POST',
    data: val
  })
}

export function findAllCourse () {

  return instance({
    url: AllPATH.findAllCoursePath,
    method: 'POST',
  })
}
export function findCourseList ({page,size}) {
  return instance({
    url: AllPATH.findCourseListPath,
    method: 'POST',
    params:{
      page:page,
      size:size
    }
  })
}

// 新增分片上传相关方法
export const uploadChunk = (formData, onProgress) => {
  return instance({
    url: AllPATH.uploadChunk,
    method: 'POST',
    data: formData,
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: onProgress
  })
}

export const mergeChunks = (params) => {
  return instance({
    url: AllPATH.mergeChunks,
    method: 'POST',
    data: params,
    headers: {
      "Content-Type": "multipart/form-data"
    }
  })
}

export const checkFileExist = (fileMD5) => {
  const formData = new FormData();
  formData.append('identifier', fileMD5);
  return instance({
    url: AllPATH.checkFileExistPath,
    method: 'POST',
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data"
    }
  })
}

export const getCourseDetail = (id)=>{
  return instance({
    url: AllPATH.getMaterialDetailPath,
    method: 'GET',
    params: {id:id},
  })
}

export const getAllCourseTypel = ()=>{
  return instance({
    url: AllPATH.getAllCourseTypePath,
    method: 'GET'
  })
}

export const deleteMaterial = (id)=>{
  return instance({
    url: AllPATH.deleteMaterialPath,
    method: 'GET',
    params: {id:id}
  })
}