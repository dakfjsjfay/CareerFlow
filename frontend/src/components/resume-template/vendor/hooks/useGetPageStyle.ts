// @ts-nocheck
import { computed } from 'vue';

// 返回最外层样式
export const useGetPageStyle = (template: any) => {
  const images = import.meta.glob('/src/assets/resume-template/images/*', { eager: true });

  const loadBackgroundImage = (backgroundPath: string, element: any) => {
    if (backgroundPath) {
      const isOnlineUrl = backgroundPath.includes('https://');
      if (isOnlineUrl) {
        return `url(${backgroundPath})`;
      } else {
        const imageKey = `/src/assets/resume-template/images/${backgroundPath}`;
        const image = images[imageKey] as { default: string }; // 类型断言
        if (image) {
          // 这里确保你取到了正确的图像 URL
          return `url(${image.default}) no-repeat`;
        } else {
          console.error(`Image not found for path: ${imageKey}`);
        }
      }
    } else {
      return element.css.background || 'none';
    }
  };

  const boxStyle = computed(() => {
    // 在这里加载背景图像
    const background: string = loadBackgroundImage(
      template.css.backgroundPath,
      template
    );
    template.css.background = background;
    return {
      width: template.css?.width
        ? typeof template.css.width === 'string'
          ? template.css.width
          : `${template.css.width}px`
        : '',
      height: template.css?.height
        ? typeof template.css.height === 'string'
          ? template.css.height
          : `${template.css.height}px`
        : '',
      background: background,
      backgroundRepeat: template.css?.backgroundRepeat || '',
      opacity: template.css.opacity ?? 1,
      fontSize: `${template.css.fontSize}px` || '',
      fontFamily: template.css.fontFamily || '微软雅黑',
      fontWeight: template.css.fontWeight || 400,
      color: template.css.color || '#000',
      display: template.css.display || 'flex',
      flex: template.css.flex || '',
      flexWrap: template.css.flexWrap || '',
      flexDirection: template.css.flexDirection || 'row',
      flexShrink: template.css?.flexShrink || 1,
      justifyContent: template.css.justifyContent || 'flex-start',
      alignItems: template.css.alignItems || 'center',
      paddingTop: template.css?.padding?.top
        ? `${template.css.padding.top}px`
        : '',
      paddingBottom: template.css?.padding?.bottom
        ? `${template.css.padding.bottom}px`
        : '',
      paddingLeft: template.css?.padding?.left
        ? `${template.css.padding.left}px`
        : '',
      paddingRight: template.css?.padding?.right
        ? `${template.css.padding.right}px`
        : '',
      marginTop: template.css?.margin?.top
        ? `${template.css.margin.top}px`
        : '',
      marginBottom: template.css?.margin?.bottom
        ? `${template.css.margin.bottom}px`
        : '',
      marginLeft: template.css?.margin?.left
        ? `${template.css.margin.left}px`
        : '',
      marginRight: template.css?.margin?.right
        ? `${template.css.margin.right}px`
        : '',
      // 边框宽度
      borderTopWidth: template.css?.borderWidth
        ? `${template.css.borderWidth.top}px`
        : '0px',
      borderRightWidth: template.css?.borderWidth
        ? `${template.css.borderWidth.right}px`
        : '0px',
      borderBottomWidth: template.css?.borderWidth
        ? `${template.css.borderWidth.bottom}px`
        : '0px',
      borderLeftWidth: template.css?.borderWidth
        ? `${template.css.borderWidth.left}px`
        : '0px',

      borderColor: template.css.borderColor || '#eee',
      // 圆角
      borderTopLeftRadius: template.css?.borderRadius?.topLeft
        ? typeof template.css?.borderRadius?.topLeft === 'string'
          ? template.css?.borderRadius?.topLeft
          : `${template.css?.borderRadius?.topLeft}px`
        : '',
      borderTopRightRadius: template.css?.borderRadius?.topRight
        ? typeof template.css?.borderRadius?.topRight === 'string'
          ? template.css?.borderRadius?.topRight
          : `${template.css?.borderRadius?.topRight}px`
        : '',
      borderBottomLeftRadius: template.css?.borderRadius?.bottomLeft
        ? typeof template.css?.borderRadius?.bottomLeft === 'string'
          ? template.css?.borderRadius?.bottomLeft
          : `${template.css?.borderRadius?.bottomLeft}px`
        : '',
      borderBottomRightRadius: template.css?.borderRadius?.bottomRight
        ? typeof template.css?.borderRadius?.bottomRight === 'string'
          ? template.css?.borderRadius?.bottomRight
          : `${template.css?.borderRadius?.bottomRight}px`
        : '',
      borderStyle: template.css.borderStyle || 'solid',
      boxShadow: template.css.boxShadow || 'none',
      zIndex: template.css.zIndex || 'auto',
      overflow: template.css.overflow || 'visible',
      textAlign: template.css.textAlign || 'left',
      lineHeight: template.css.lineHeight || 'normal',
      letterSpacing: `${template.css.letterSpacing}px` || 'normal',
      visibility: template.css.visibility || 'visible',
      position: template.css.position || 'static',
      top: template.css.top ? `${template.css.top}px` : 'auto',
      right: template.css.right ? `${template.css.right}px` : 'auto',
      bottom: template.css.bottom ? `${template.css.bottom}px` : 'auto',
      left: template.css.left ? `${template.css.left}px` : 'auto',
      cursor: template.css.cursor || 'auto',
      transition: template.css.transition || 'none',

      // 处理旋转
      transform: template.css?.rotate
        ? `rotate(${template.css.rotate}deg)`
        : template.css?.transform || '',

      // 一级标题样式
      heading1FontSize: template.css.heading1FontSize || '32px',
      heading1FontWeight: template.css.heading1FontWeight || 'bold',
      heading1Color: template.css.heading1Color || '#000',
      heading1LineHeight: template.css.heading1LineHeight || '1.5',
      heading1LetterSpacing: template.css.heading1LetterSpacing || 'normal',

      // 二级标题样式
      heading2FontSize: template.css.heading2FontSize || '24px',
      heading2FontWeight: template.css.heading2FontWeight || 'bold',
      heading2Color: template.css.heading2Color || '#333',
      heading2LineHeight: template.css.heading2LineHeight || '1.4',
      heading2LetterSpacing: template.css.heading2LetterSpacing || 'normal',

      // 三级标题样式
      heading3FontSize: template.css.heading3FontSize || '18px',
      heading3FontWeight: template.css.heading3FontWeight || 'bold',
      heading3Color: template.css.heading3Color || '#666',
      heading3LineHeight: template.css.heading3LineHeight || '1.3',
      heading3LetterSpacing: template.css.heading3LetterSpacing || 'normal',

      // 正文字体样式
      bodyFontSize: template.css.bodyFontSize || '14px',
      bodyFontWeight: template.css.bodyFontWeight || 'normal',
      bodyColor: template.css.bodyColor || '#000',
      bodyLineHeight: template.css.bodyLineHeight || '1.6',
      bodyLetterSpacing: template.css.bodyLetterSpacing || 'normal',

      // 主题颜色
      themeColor: template.css?.themeColor || ''
    };
  });
  return boxStyle;
};
