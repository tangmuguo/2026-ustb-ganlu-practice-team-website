import instance from "@/utils/http"
import AllPATH from "@/utils/path"

// 获取轮播图列表
export const getBannerList = () => {
  return instance({
    url: AllPATH.bannerListPath,
    method: 'get'
  })
}

// 添加轮播图
export const addBanner = (data) => {
  return instance({
    url: AllPATH.bannerAddPath,
    method: 'post',
    data
  })
}

// 更新轮播图
export const updateBanner = (data) => {
  return instance({
    url: AllPATH.bannerUpdatePath,
    method: 'post',
    data
  })
}

// 删除轮播图
export const deleteBanner = (id) => {
  return instance({
    url: AllPATH.bannerDeletePath,
    method: 'post',
    data: { id }
  })
}

// 更新排序
export const updateBannerSort = (id, sortOrder) => {
  return instance({
    url: AllPATH.bannerUpdateSortPath,
    method: 'post',
    data: { id, sortOrder }
  })
}

// 更新状态
export const updateBannerStatus = (id, isVisible) => {
  return instance({
    url: AllPATH.bannerUpdateStatusPath,
    method: 'post',
    data: { id, isVisible }
  })
}

// 更新链接
export const updateBannerLink = (id, linkUrl) => {
  return instance({
    url: AllPATH.bannerUpdateLinkPath,
    method: 'post',
    data: { id, linkUrl }
  })
}